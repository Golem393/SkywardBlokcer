# Publishing a new SkywardBlocker release

```bash
cd ~/Projects/skyward/SkywardBlokcer
```

## 1. Bump the version

In `app/build.gradle.kts`, increase **both**:

```kotlin
versionCode = 4      // must be strictly higher than the last release
versionName = "1.4"  // cosmetic, shown to the parent
```

`versionCode` is the only thing compared to decide "is an update available?" — forgetting
to bump it means every phone reports "up to date" and the new build is silently never
installed.

## 2. Build and get the values

```bash
./gradlew assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
sha256sum $APK && stat -c%s $APK
#apksigner verify --print-certs $APK | head -1   # must NOT say CN=Android Debug
```

Keep the sha256 and byte size — they go into the SQL insert in step 4.

## 3. Upload the APK

Name it `skywardblocker-<versionCode>.apk`, matching step 1 exactly.

```bash
curl -X POST \
  "https://<project-ref>.supabase.co/storage/v1/object/app-releases/skywardblocker-4.apk" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
  --data-binary @$APK
```

A `{"Key":"app-releases/skywardblocker-4.apk"}` response means it landed.

## 4. Publish the row

Supabase SQL editor:

```sql
begin;

insert into app_releases (version_code, version_name, storage_path, sha256, size_bytes, release_notes)
values (4, '1.4', 'skywardblocker-4.apk', '<sha256 from step 2>', <bytes from step 2>, 'What changed.');

update app_releases set is_current = false where is_current;
update app_releases set is_current = true where version_code = 4;

commit;
```

## 5. Verify

```sql
select r.version_code, r.storage_path, o.name is not null as uploaded
from app_releases r
left join storage.objects o on o.bucket_id = 'app-releases' and o.name = r.storage_path
where r.is_current;
```

`uploaded` must be `true`. If it's `false`, the row is published but the file is missing
from the bucket — the desktop app will fail with a "could not sign the download URL" error
until step 3 is redone.

---

**The four numbers must all agree:** `versionCode` in Gradle, the version in the storage
filename, and the `version_code` + `sha256` in the SQL row. A mismatch anywhere in this
chain either fails the download's hash check or silently ships the wrong APK.

**Rolling back** a bad release: point `is_current` at an older `version_code` (steps 4–5
only, no re-upload needed). Note this only protects phones that haven't updated yet —
Android refuses to downgrade a device already on the newer build, so a bad release still
has to be fixed forward with a new, higher versionCode.
