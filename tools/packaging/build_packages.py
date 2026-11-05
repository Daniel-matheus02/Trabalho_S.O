#!/usr/bin/env python3
"""Gera os executáveis autônomos de Windows e Linux do Duelo de Fichas.

Os dois executáveis são autocontidos: embutem um runtime Temurin Java 21 além dos
fat jars do cliente e do servidor, então o jogador não precisa de Java, Gradle nem
conexão com a internet.

    python tools/packaging/build_packages.py

Saídas (dist/):
    DueloDeFichas.exe          inicializador + instalador por usuário do Windows
    DueloDeFichas-linux.run    inicializador autoextraível do Linux
    README.txt                 instruções curtas para os jogadores
    SHA256SUMS.txt

Requisitos da máquina de compilação: Python 3.8+, acesso à internet na primeira
execução (todos os downloads são armazenados fora do repositório) e Windows para
compilar o .exe (o csc.exe acompanha o .NET Framework, sem necessidade de SDK).
"""

from __future__ import annotations

import argparse
import hashlib
import io
import os
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
import zlib
from pathlib import Path

PACKAGING_DIR = Path(__file__).resolve().parent
ROOT = PACKAGING_DIR.parents[1]
DIST = ROOT / "dist"

ADOPTIUM = "https://api.adoptium.net/v3/binary/latest/21/ga/{os}/{arch}/{kind}/hotspot/normal/eclipse"
JDK_WIN_URL = ADOPTIUM.format(os="windows", arch="x64", kind="jdk")
JRE_WIN_URL = ADOPTIUM.format(os="windows", arch="x64", kind="jre")
JRE_LINUX_URL = ADOPTIUM.format(os="linux", arch="x64", kind="jre")

PAYLOAD_SCHEMA = "1"

ICON_SIZES = (16, 24, 32, 48, 64, 128, 256)
ICON_RENDER_SIZE = 512


def log(message: str) -> None:
    print(message, flush=True)


def fail(message: str) -> "NoReturn":  # type: ignore[name-defined]
    print("erro: " + message, file=sys.stderr, flush=True)
    raise SystemExit(1)


def remove_tree(path: Path) -> None:
    if not path.exists():
        return
    shutil.rmtree(path, ignore_errors=True)


# ---------------------------------------------------------------------------
# Localização das entradas
# ---------------------------------------------------------------------------
def default_cache_dir() -> Path:
    override = os.environ.get("DUELO_BUILD_CACHE")
    if override:
        return Path(override).expanduser()
    return Path.home() / ".cache" / "duelo-de-fichas-build"


def read_game_version() -> str:
    text = (ROOT / "build.gradle").read_text(encoding="utf-8")
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("version") and "=" in stripped:
            value = stripped.split("=", 1)[1].strip().strip("'\"")
            if value:
                return value
    fail("não foi possível ler a versão do projeto em build.gradle")


def download(url: str, destination: Path) -> Path:
    if destination.is_file() and destination.stat().st_size > 1_000_000:
        return destination

    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    log("  baixando %s" % url)
    request = urllib.request.Request(url, headers={"User-Agent": "duelo-de-fichas-build"})
    with urllib.request.urlopen(request, timeout=120) as response, open(temporary, "wb") as out:
        total = int(response.headers.get("Content-Length") or 0)
        received = 0
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            out.write(chunk)
            received += len(chunk)
            if total:
                sys.stdout.write("\r    %5.1f%%" % (received * 100.0 / total))
                sys.stdout.flush()
        sys.stdout.write("\r    %5.1f%%\n" % (100.0 if not total else received * 100.0 / total))
    if temporary.stat().st_size < 1_000_000:
        temporary.unlink(missing_ok=True)
        fail("o download falhou ou foi truncado: " + url)
    temporary.replace(destination)
    return destination


def cached_jdk(cache: Path) -> Path:
    archive = download(JDK_WIN_URL, cache / "jdk21-win.zip")
    target = cache / "jdk21-win"
    jdk = find_single_child_dir(target)
    if jdk is not None and (jdk / "bin" / "java.exe").is_file():
        return jdk
    target.mkdir(parents=True, exist_ok=True)
    log("  extraindo o JDK")
    with zipfile.ZipFile(archive) as archive_file:
        archive_file.extractall(target)
    jdk = find_single_child_dir(target)
    if jdk is None or not (jdk / "bin" / "java.exe").is_file():
        fail("o arquivo do JDK baixado tem uma estrutura inesperada")
    return jdk


def cached_jre_windows(cache: Path) -> Path:
    archive = download(JRE_WIN_URL, cache / "jre21-win.zip")
    target = cache / "jre21-win"
    jre = find_single_child_dir(target)
    if jre is not None and (jre / "bin" / "java.exe").is_file():
        return jre
    target.mkdir(parents=True, exist_ok=True)
    log("  extraindo o JRE do Windows")
    with zipfile.ZipFile(archive) as archive_file:
        archive_file.extractall(target)
    jre = find_single_child_dir(target)
    if jre is None or not (jre / "bin" / "java.exe").is_file():
        fail("o arquivo do JRE do Windows baixado tem uma estrutura inesperada")
    return jre


def cached_jre_linux(cache: Path) -> Path:
    archive = download(JRE_LINUX_URL, cache / "jre21-linux.tar.gz")
    with tarfile.open(archive, "r:gz") as tar:
        members = tar.getmembers()
    if not members:
        fail("o JRE do Linux baixado está vazio")
    top = members[0].name.split("/")[0].rstrip("/")
    if not any(member.name == top + "/bin/java" for member in members):
        fail("o JRE do Linux baixado não contém bin/java")
    return archive


def find_single_child_dir(parent: Path) -> "Path | None":
    if not parent.is_dir():
        return None
    children = [child for child in parent.iterdir() if child.is_dir()]
    return children[0] if len(children) == 1 else None


def jre_version(jre_dir: Path) -> str:
    release = jre_dir / "release"
    if not release.is_file():
        return "unknown"
    return _parse_release(release.read_text(encoding="utf-8", errors="replace"))


def jre_version_from_tar(jre_tar: Path) -> str:
    with tarfile.open(jre_tar, "r:gz") as tar:
        members = tar.getmembers()
        if not members:
            return "unknown"
        top = members[0].name.split("/")[0]
        handle = tar.extractfile(top + "/release") if any(
            member.name == top + "/release" for member in members
        ) else None
        if handle is None:
            return "unknown"
        return _parse_release(handle.read().decode("utf-8", errors="replace"))


def _parse_release(text: str) -> str:
    for line in text.splitlines():
        if line.startswith("JAVA_RUNTIME_VERSION="):
            return line.split("=", 1)[1].strip().strip('"')
    return "unknown"


# ---------------------------------------------------------------------------
# Jars do jogo
# ---------------------------------------------------------------------------
def build_game_jars(jdk: Path) -> list[Path]:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    environment = dict(os.environ)
    environment["JAVA_HOME"] = str(jdk)
    environment["PATH"] = str(jdk / "bin") + os.pathsep + environment.get("PATH", "")

    log("Gerando os jars do cliente e do servidor com %s" % jdk)
    command = [str(wrapper), "--no-daemon", "--console=plain", "clientJar", "serverJar"]
    result = subprocess.run(command, cwd=ROOT, env=environment)
    if result.returncode != 0:
        fail("a compilação do Gradle falhou")

    version = read_game_version()
    jars = [
        ROOT / "build" / "libs" / ("duelo-de-fichas-%s.jar" % version),
        ROOT / "build" / "libs" / ("duelo-server-%s.jar" % version),
    ]
    for jar in jars:
        if not jar.is_file():
            fail("o jar esperado não foi gerado: " + str(jar))
    return jars


# ---------------------------------------------------------------------------
# Ícone (Python puro: sem Pillow na máquina de compilação)
# ---------------------------------------------------------------------------
def _rounded(x: float, y: float, x0: float, y0: float, x1: float, y1: float, radius: float) -> bool:
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    cx = min(max(x, x0 + radius), x1 - radius)
    cy = min(max(y, y0 + radius), y1 - radius)
    dx = x - cx
    dy = y - cy
    return dx * dx + dy * dy <= radius * radius


def _heart(x: float, y: float, cx: float, cy: float, scale: float) -> bool:
    u = (x - cx) / scale
    v = (cy - y) / scale
    v = v + 0.15
    value = (u * u + v * v - 0.35) ** 3 - u * u * (v + 0.15) ** 3 * 1.6
    return value <= 0.0


class _Canvas:
    def __init__(self, width: int, height: int) -> None:
        self.width = width
        self.height = height
        self.pixels = bytearray(width * height * 4)

    def blend(self, x: int, y: int, red: int, green: int, blue: int, alpha: float) -> None:
        if alpha <= 0.0 or x < 0 or y < 0 or x >= self.width or y >= self.height:
            return
        index = (y * self.width + x) * 4
        source_alpha = min(1.0, alpha)
        dest_alpha = self.pixels[index + 3] / 255.0
        out_alpha = source_alpha + dest_alpha * (1.0 - source_alpha)
        if out_alpha <= 0.0:
            return
        for offset, value in enumerate((red, green, blue)):
            dest = self.pixels[index + offset]
            self.pixels[index + offset] = int(
                round((value * source_alpha + dest * dest_alpha * (1.0 - source_alpha)) / out_alpha)
            )
        self.pixels[index + 3] = int(round(out_alpha * 255))


def _resize(source: bytearray, width: int, height: int, target: int) -> bytearray:
    output = bytearray(target * target * 4)
    for y in range(target):
        y0 = y * height // target
        y1 = max(y0 + 1, (y + 1) * height // target)
        for x in range(target):
            x0 = x * width // target
            x1 = max(x0 + 1, (x + 1) * width // target)
            red = green = blue = alpha = 0.0
            count = 0
            for sy in range(y0, y1):
                row = sy * width
                for sx in range(x0, x1):
                    index = (row + sx) * 4
                    pixel_alpha = source[index + 3] / 255.0
                    red += source[index] * pixel_alpha
                    green += source[index + 1] * pixel_alpha
                    blue += source[index + 2] * pixel_alpha
                    alpha += pixel_alpha
                    count += 1
            out_index = (y * target + x) * 4
            if alpha > 0.0:
                output[out_index] = min(255, int(round(red / alpha)))
                output[out_index + 1] = min(255, int(round(green / alpha)))
                output[out_index + 2] = min(255, int(round(blue / alpha)))
                output[out_index + 3] = min(255, int(round(alpha / count * 255)))
    return output


def _render_icon(size: int) -> bytearray:
    canvas = _Canvas(size, size)
    s = float(size)
    felt_top = (0x18, 0x5c, 0x46)
    felt_bottom = (0x07, 0x2b, 0x21)
    gold = (0xd8, 0xb0, 0x4a)
    card = (0xf7, 0xf3, 0xe6)
    card_edge = (0xb8, 0xb0, 0x9c)
    red = (0xb3, 0x26, 0x1e)

    outer = (0.015 * s, 0.015 * s, 0.985 * s, 0.985 * s, 0.20 * s)
    border = (0.055 * s, 0.055 * s, 0.945 * s, 0.945 * s, 0.165 * s)
    border_inner = (0.085 * s, 0.085 * s, 0.915 * s, 0.915 * s, 0.145 * s)

    back_card = (0.315 * s, 0.215 * s, 0.815 * s, 0.755 * s, 0.035 * s)
    front_card = (0.185 * s, 0.305 * s, 0.685 * s, 0.845 * s, 0.035 * s)

    for y in range(size):
        t = y / max(1.0, s - 1.0)
        row_red = int(felt_top[0] + (felt_bottom[0] - felt_top[0]) * t)
        row_green = int(felt_top[1] + (felt_bottom[1] - felt_top[1]) * t)
        row_blue = int(felt_top[2] + (felt_bottom[2] - felt_top[2]) * t)
        for x in range(size):
            if not _rounded(x, y, *outer):
                continue
            canvas.blend(x, y, row_red, row_green, row_blue, 1.0)

            if _rounded(x, y, *border) and not _rounded(x, y, *border_inner):
                canvas.blend(x, y, gold[0], gold[1], gold[2], 1.0)

            # Sombras das cartas.
            if _rounded(x, y, back_card[0] + 0.012 * s, back_card[1] + 0.018 * s,
                        back_card[2] + 0.012 * s, back_card[3] + 0.018 * s, back_card[4]):
                canvas.blend(x, y, 0, 0, 0, 0.28)
            if _rounded(x, y, front_card[0] + 0.012 * s, front_card[1] + 0.018 * s,
                        front_card[2] + 0.012 * s, front_card[3] + 0.018 * s, front_card[4]):
                canvas.blend(x, y, 0, 0, 0, 0.32)

            # Carta de trás e seu losango.
            if _rounded(x, y, *back_card):
                edge = (
                    not _rounded(x, y, back_card[0] + 0.008 * s, back_card[1] + 0.008 * s,
                                 back_card[2] - 0.008 * s, back_card[3] - 0.008 * s,
                                 max(0.0, back_card[4] - 0.008 * s))
                )
                colour = card_edge if edge else card
                canvas.blend(x, y, colour[0], colour[1], colour[2], 1.0)
                diamond_x = 0.685 * s
                diamond_y = 0.345 * s
                diamond_r = 0.050 * s
                if abs(x - diamond_x) + abs(y - diamond_y) <= diamond_r:
                    canvas.blend(x, y, red[0], red[1], red[2], 1.0)

            # Carta da frente e seu coração.
            if _rounded(x, y, *front_card):
                edge = (
                    not _rounded(x, y, front_card[0] + 0.008 * s, front_card[1] + 0.008 * s,
                                 front_card[2] - 0.008 * s, front_card[3] - 0.008 * s,
                                 max(0.0, front_card[4] - 0.008 * s))
                )
                colour = card_edge if edge else card
                canvas.blend(x, y, colour[0], colour[1], colour[2], 1.0)
                if _heart(x, y, 0.435 * s, 0.600 * s, 0.165 * s):
                    canvas.blend(x, y, red[0], red[1], red[2], 1.0)

    return canvas.pixels


def _png_bytes(width: int, height: int, rgba: bytearray) -> bytes:
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(rgba[y * stride:(y + 1) * stride])

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + kind
            + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def write_icons(destination: Path) -> Path:
    log("Desenhando o ícone do aplicativo")
    master = _render_icon(ICON_RENDER_SIZE)
    rendered: dict[int, bytes] = {}
    for size in ICON_SIZES:
        rendered[size] = _png_bytes(size, size, _resize(master, ICON_RENDER_SIZE, ICON_RENDER_SIZE, size))

    ico_path = destination / "icon.ico"
    count = len(ICON_SIZES)
    header = struct.pack("<HHH", 0, 1, count)
    entries = bytearray()
    data = bytearray()
    offset = 6 + 16 * count
    for size in sorted(ICON_SIZES):
        png = rendered[size]
        dimension = 0 if size >= 256 else size
        entries.extend(struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32, len(png), offset))
        offset += len(png)
        data.extend(png)
    ico_path.write_bytes(bytes(header) + bytes(entries) + bytes(data))

    png_path = destination / "icon.png"
    png_path.write_bytes(rendered[256])
    return ico_path


# ---------------------------------------------------------------------------
# Montagem do payload
# ---------------------------------------------------------------------------
def stage_common_payload(staging: Path, game_version: str, jars: list[Path], icon_png: Path) -> None:
    game_dir = staging / "game"
    game_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jars[0], game_dir / jars[0].name)
    shutil.copy2(jars[1], game_dir / jars[1].name)

    config_source = ROOT / "config" / "server.properties"
    if config_source.is_file():
        config_dir = staging / "config"
        config_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(config_source, config_dir / "server.properties")

    shutil.copy2(icon_png, staging / "icon.png")

    (staging / "game" / "README.txt").write_text(
        "Duelo de Fichas %s\n"
        "Esta pasta é gerenciada pelo inicializador. Execute o executável original novamente para atualizar.\n"
        % game_version,
        encoding="utf-8",
    )


def payload_version_for(game_version: str, jars: list[Path], runtime_version: str) -> str:
    digest = hashlib.sha256()
    digest.update(PAYLOAD_SCHEMA.encode("ascii"))
    digest.update(game_version.encode("ascii"))
    for jar in jars:
        with open(jar, "rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(block)
    digest.update(runtime_version.encode("utf-8"))
    return "%s-%s" % (game_version, digest.hexdigest()[:12])


def zip_windows_payload(staging: Path, output: Path) -> None:
    log("Compactando o payload do Windows")
    files: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(staging):
        dirnames.sort()
        for name in sorted(filenames):
            files.append(Path(dirpath) / name)

    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            arcname = path.relative_to(staging).as_posix()
            info = zipfile.ZipInfo(arcname, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            info.file_size = path.stat().st_size
            with open(path, "rb") as source, archive.open(info, "w") as dest:
                shutil.copyfileobj(source, dest, 1024 * 1024)


def build_linux_payload(common: Path, jre_tar: Path, output: Path) -> None:
    log("Compactando o payload do Linux (jogo + runtime Java)")
    import gzip

    with open(output, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0) as gz:
            with tarfile.open(fileobj=gz, mode="w") as tar:
                for dirpath, dirnames, filenames in os.walk(common):
                    dirnames.sort()
                    relative = os.path.relpath(dirpath, common)
                    if relative != ".":
                        info = tarfile.TarInfo(relative.replace(os.sep, "/"))
                        info.type = tarfile.DIRTYPE
                        info.mode = 0o755
                        info.mtime = 0
                        info.uid = info.gid = 0
                        info.uname = info.gname = "root"
                        tar.addfile(info)
                    for name in sorted(filenames):
                        path = Path(dirpath) / name
                        arcname = (relative + os.sep + name) if relative != "." else name
                        data = path.read_bytes()
                        info = tarfile.TarInfo(arcname.replace(os.sep, "/"))
                        info.size = len(data)
                        info.mode = 0o644
                        info.mtime = 0
                        info.uid = info.gid = 0
                        info.uname = info.gname = "root"
                        tar.addfile(info, io.BytesIO(data))

                with tarfile.open(jre_tar, "r:gz") as source:
                    members = source.getmembers()
                    top = members[0].name.split("/")[0]
                    for member in members:
                        if member.name == top:
                            continue
                        relative = member.name[len(top):].lstrip("/")
                        if not relative:
                            continue
                        info = tarfile.TarInfo("runtime/" + relative)
                        info.type = member.type
                        info.mode = member.mode
                        info.mtime = 0
                        info.uid = info.gid = 0
                        info.uname = info.gname = "root"
                        info.linkname = member.linkname
                        info.size = member.size
                        if member.isreg():
                            tar.addfile(info, source.extractfile(member))
                        else:
                            tar.addfile(info)


# ---------------------------------------------------------------------------
# Executável do Windows
# ---------------------------------------------------------------------------
def find_csc() -> Path:
    candidates = [
        Path(os.environ.get("WINDIR", r"C:\Windows")) / "Microsoft.NET" / "Framework64" / "v4.0.30319" / "csc.exe",
        Path(os.environ.get("WINDIR", r"C:\Windows")) / "Microsoft.NET" / "Framework" / "v4.0.30319" / "csc.exe",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    fail("csc.exe (compilador C# do .NET Framework) não foi encontrado; o executável do Windows precisa ser gerado no Windows")


def build_windows_executable(
    work: Path,
    build_dir: Path,
    payload_zip: Path,
    ico: Path,
    game_version: str,
    payload_version: str,
    output: Path,
) -> None:
    log("Compilando o inicializador do Windows")
    source = (PACKAGING_DIR / "windows" / "Launcher.cs").read_text(encoding="utf-8")
    source = source.replace("@GAME_VERSION@", game_version).replace("@PAYLOAD_VERSION@", payload_version)
    generated = work / "Launcher.g.cs"
    generated.write_text(source, encoding="utf-8")

    csc = find_csc()
    command = [
        str(csc),
        "/nologo",
        "/target:winexe",
        "/platform:anycpu",
        "/optimize+",
        "/langversion:5",
        "/win32icon:" + str(ico),
        "/out:" + str(output),
        "/resource:" + str(payload_zip) + ",payload.zip",
        "/r:System.dll",
        "/r:System.Drawing.dll",
        "/r:System.Windows.Forms.dll",
        "/r:System.IO.Compression.dll",
        "/r:System.IO.Compression.FileSystem.dll",
        str(generated),
    ]
    result = subprocess.run(command, cwd=build_dir)
    if result.returncode != 0 or not output.is_file():
        fail("o csc não conseguiu gerar o executável do Windows")


# ---------------------------------------------------------------------------
# Executável do Linux
# ---------------------------------------------------------------------------
def build_linux_executable(
    payload: Path, game_version: str, payload_version: str, output: Path
) -> None:
    log("Montando o inicializador autoextraível do Linux")
    template = (PACKAGING_DIR / "linux" / "launcher.sh.in").read_text(encoding="utf-8")
    if "\r" in template:
        template = template.replace("\r\n", "\n").replace("\r", "\n")

    header = b""
    offset = 0
    for _ in range(6):
        rendered = (
            template.replace("__GAME_VERSION__", game_version)
            .replace("__PAYLOAD_VERSION__", payload_version)
            .replace("__PAYLOAD_OFFSET__", str(offset))
        ).encode("utf-8")
        if len(rendered) == offset:
            header = rendered
            break
        offset = len(rendered)
        header = rendered
    else:
        fail("não foi possível estabilizar o deslocamento do payload do inicializador Linux")

    if offset <= 0:
        fail("o cabeçalho do inicializador Linux está vazio")

    with open(output, "wb") as handle:
        handle.write(header)
        with open(payload, "rb") as source:
            shutil.copyfileobj(source, handle, 1024 * 1024)
    os.chmod(output, 0o755)


# ---------------------------------------------------------------------------
# Execução principal
# ---------------------------------------------------------------------------
def write_player_readme(destination: Path, game_version: str) -> None:
    (destination / "README.txt").write_text(
        "Duelo de Fichas %s - compilação autônoma\n"
        "========================================\n\n"
        "Os dois arquivos são instaladores completos: eles embutem o Java 21 e o jogo,\n"
        "então nada precisa ser instalado antes e não é necessária conexão com a internet.\n\n"
        "Windows\n"
        "-------\n"
        "  Clique duas vezes em DueloDeFichas.exe.\n"
        "  A primeira execução descompacta o jogo em %%LOCALAPPDATA%%\\DueloDeFichas\n"
        "  e cria um atalho no Menu Iniciar. As execuções seguintes são imediatas.\n\n"
        "  Servidor dedicado de LAN (opcional):\n"
        "    DueloDeFichas.exe --server --port=7777\n\n"
        "Linux (x86-64)\n"
        "--------------\n"
        "  chmod +x DueloDeFichas-linux.run\n"
        "  ./DueloDeFichas-linux.run\n\n"
        "  A primeira execução descompacta o jogo em ~/.local/share/duelo-de-fichas/app\n"
        "  e registra uma entrada na área de trabalho. As execuções seguintes são imediatas.\n\n"
        "  Servidor dedicado de LAN (opcional):\n"
        "    ./DueloDeFichas-linux.run --server --port=7777\n\n"
        "O jogo precisa de OpenGL 2.0+ (qualquer área de trabalho normal com aceleração de GPU).\n"
        "O multiplayer em LAN embutido no jogo funciona sem esse servidor dedicado.\n"
        % game_version,
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Gera os executáveis do Duelo de Fichas.")
    parser.add_argument("--cache-dir", type=Path, default=None, help="local do cache de downloads")
    parser.add_argument("--work-dir", type=Path, default=None, help="diretório temporário de compilação")
    parser.add_argument("--skip-build", action="store_true", help="reutiliza os jars existentes em build/libs")
    parser.add_argument("--only", choices=("windows", "linux", "both"), default="both")
    arguments = parser.parse_args()

    cache = (arguments.cache_dir or default_cache_dir()).resolve()
    cache.mkdir(parents=True, exist_ok=True)
    DIST.mkdir(parents=True, exist_ok=True)

    game_version = read_game_version()
    log("Duelo de Fichas %s" % game_version)
    log("  cache: %s" % cache)

    jdk = cached_jdk(cache)
    if arguments.skip_build:
        jars = [
            ROOT / "build" / "libs" / ("duelo-de-fichas-%s.jar" % game_version),
            ROOT / "build" / "libs" / ("duelo-server-%s.jar" % game_version),
        ]
        for jar in jars:
            if not jar.is_file():
                fail("--skip-build foi solicitado, mas %s não existe" % jar)
    else:
        jars = build_game_jars(jdk)

    if arguments.work_dir is not None:
        build_dir = arguments.work_dir.resolve()
        remove_tree(build_dir)
        build_dir.mkdir(parents=True, exist_ok=True)
    else:
        # Um diretório novo por execução: um antivírus pode bloquear brevemente os
        # arquivos da execução anterior, e uma limpeza malsucedida não deve abortar
        # uma nova compilação.
        build_dir = Path(tempfile.mkdtemp(prefix="build-", dir=str(cache)))
    log("  work:  %s" % build_dir)

    icons_dir = build_dir / "icons"
    icons_dir.mkdir(parents=True, exist_ok=True)
    ico = write_icons(icons_dir)

    common = build_dir / "common"
    stage_common_payload(common, game_version, jars, icons_dir / "icon.png")

    artifacts: list[Path] = []

    if arguments.only in ("windows", "both"):
        jre_win = cached_jre_windows(cache)
        version = payload_version_for(game_version, jars, jre_version(jre_win))
        stage = build_dir / "payload-windows"
        shutil.copytree(common, stage)
        log("Copiando o runtime Java do Windows")
        shutil.copytree(jre_win, stage / "runtime")
        payload = build_dir / "payload-windows.zip"
        zip_windows_payload(stage, payload)
        executable = DIST / "DueloDeFichas.exe"
        build_windows_executable(build_dir, build_dir, payload, ico, game_version, version, executable)
        artifacts.append(executable)

    if arguments.only in ("linux", "both"):
        jre_linux = cached_jre_linux(cache)
        linux_runtime_version = jre_version_from_tar(jre_linux)
        version = payload_version_for(game_version, jars, linux_runtime_version)
        payload = build_dir / "payload-linux.tar.gz"
        build_linux_payload(common, jre_linux, payload)
        executable = DIST / "DueloDeFichas-linux.run"
        build_linux_executable(payload, game_version, version, executable)
        artifacts.append(executable)

    write_player_readme(DIST, game_version)

    checksums = []
    for artifact in artifacts:
        digest = hashlib.sha256()
        with open(artifact, "rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(block)
        checksums.append("%s  %s" % (digest.hexdigest(), artifact.name))
        log("  %-32s %8.1f MB" % (artifact.name, artifact.stat().st_size / (1024 * 1024)))
    (DIST / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")

    log("Concluído. Os artefatos estão em %s" % DIST)


if __name__ == "__main__":
    main()
