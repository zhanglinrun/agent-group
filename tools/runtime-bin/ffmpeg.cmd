@echo off
setlocal
set "LOCAL_FFMPEG=%~dp0ffmpeg.exe"
if exist "%LOCAL_FFMPEG%" (
  "%LOCAL_FFMPEG%" %*
  exit /b
)
python -c "import imageio_ffmpeg, subprocess, sys; sys.exit(subprocess.call([imageio_ffmpeg.get_ffmpeg_exe()] + sys.argv[1:]))" %*
exit /b
