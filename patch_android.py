"""Agrega al proyecto Android generado el código propio y los permisos de 'Abrir con'."""
import pathlib, shutil

manifest = pathlib.Path('android/app/src/main/AndroidManifest.xml')
t = manifest.read_text(encoding='utf-8')

mimes = ['application/x-cbr', 'application/x-cbz', 'application/vnd.comicbook-rar',
         'application/vnd.comicbook+zip', 'application/x-rar-compressed', 'application/vnd.rar',
         'application/x-rar', 'application/zip', 'application/x-zip-compressed',
         'application/octet-stream']
filters = '''
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" />
                <data android:scheme="file" />
''' + ''.join('                <data android:mimeType="%s" />\n' % m for m in mimes) + '''            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="file" />
                <data android:scheme="content" />
                <data android:mimeType="*/*" />
                <data android:pathPattern=".*\\\\.cbr" />
                <data android:pathPattern=".*\\\\.cbz" />
                <data android:pathPattern=".*\\\\..*\\\\.cbr" />
                <data android:pathPattern=".*\\\\..*\\\\.cbz" />
            </intent-filter>
'''
assert '</activity>' in t, 'No se encontró la actividad principal en AndroidManifest.xml'
t = t.replace('</activity>', filters + '        </activity>', 1)
manifest.write_text(t, encoding='utf-8')

dst = pathlib.Path('android/app/src/main/java/com/visorcbr/app')
dst.mkdir(parents=True, exist_ok=True)
for f in ('MainActivity.java', 'VisorPlugin.java'):
    shutil.copy('ci/native/' + f, dst / f)
print('Código propio agregado')
