import sys
import zipfile

source, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(source) as zin, zipfile.ZipFile(out, "w") as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        zout.writestr(item, data, compress_type=item.compress_type)
    zout.write(dex, "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
