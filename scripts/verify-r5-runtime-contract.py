import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path


def run(args):
    args.output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.jar) as archive:
        for name in archive.namelist():
            if not name.endswith("/") and name.startswith(("BOOT-INF/classes/", "BOOT-INF/lib/")):
                target = (args.output / name).resolve()
                if not target.is_relative_to(args.output.resolve()):
                    raise ValueError("Unsafe runtime archive member")
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
    classpath = str(args.output / "BOOT-INF/classes") + ";" + str(args.output / "BOOT-INF/lib/*")
    helper = Path(__file__).with_name("R5RuntimeContractCheck.java")
    for command in ([str(args.java / "bin/javac.exe"), "-encoding", "UTF-8", "-cp", classpath, "-d", str(args.output), str(helper)],
                    [str(args.java / "bin/java.exe"), "-cp", str(args.output) + ";" + classpath, "R5RuntimeContractCheck"]):
        result = subprocess.run(command, capture_output=True, encoding="utf-8", timeout=60)
        if result.returncode:
            raise ValueError("Pinned runtime contract failed: " + result.stderr[:1000])
    proof = {"image": args.image, "jarSha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(), "tests": 5,
             "status": "PASS", "helperSha256": hashlib.sha256(helper.read_bytes()).hexdigest(),
             "absoluteCrossDimensionInputsTested": False, "liveApiOrMobileAcceptance": False}
    (args.output / "runtime-proof.json").write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(proof, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args())
