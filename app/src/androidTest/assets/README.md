# Native reward layout fixture

`reward-fixture.mp4` is a synthetic solid-color 720×1280 H.264 Baseline video, 24 fps, exactly 8 seconds, no audio. It is only an androidTest asset, excluded from release APKs. It is not a publisher ad, an impression, or revenue.

Reproduce with FFmpeg:

```sh
ffmpeg -f lavfi -i color=c=0x16213e:s=720x1280:r=24 -t 8 -c:v libx264 -preset ultrafast -profile:v baseline -level 3.1 -pix_fmt yuv420p -movflags +faststart reward-fixture.mp4
```
