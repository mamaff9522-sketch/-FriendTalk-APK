# Vendored from 3Jump Filter Studio

`filterengine/` and `facetracking/` (FaceRegions, FaceTracker, MediaPipeRegionCheck, OneEuroFilter) are copies of
`mamaff9522-sketch/3jump-filter-studio` `app/src/main/java/com/threejump/filterstudio/` at commit 82f417c
(branch fix/home-button-text), Apache-2.0 (see that repository's LICENSE and NOTICE; parts adapted from Google
MediaPipe Samples). They are used unchanged so a filter.json exported by the Studio renders exactly the same in
calls, except one marked addition in `FilterRenderer.kt` ("3Jump SDK addition"): `drawExternal`, which renders
from an existing GL texture into the bound framebuffer (the camera frame of a call) instead of an uploaded bitmap.
Update by copying the files again and re-applying that addition.
