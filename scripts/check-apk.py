import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    libs = [n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')]
    assert libs, 'APK contains no native libraries'
    assert {n.split('/')[1] for n in libs} == {'arm64-v8a'}, libs
    assert any(n.endswith('/libexult-android-wrapper.so') for n in libs), libs
    assert apk.testzip() is None, 'APK ZIP is corrupt'
    print('APK archive and ARM64-only native libraries verified:', len(libs), 'libraries')
