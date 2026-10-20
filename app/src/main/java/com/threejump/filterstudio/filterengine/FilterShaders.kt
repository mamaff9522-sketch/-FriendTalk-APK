/*
 * GLSL ES 1.00 shaders - the exact code the app renders with and exports.
 * Licensed under the Apache License, Version 2.0.
 */
package com.threejump.filterstudio.filterengine

object FilterShaders {
    /** Uniform names set by [FilterRenderer]; checked against the sources by unit tests. */
    val UNIFORMS = listOf(
        "uTex", "uMask", "uCrop", "uMirror", "uTexel", "uBypass", "uIntensity",
        "uSmoothOn", "uSmooth", "uRadius", "uEdge",
        "uBrightOn", "uBright", "uContrast", "uSat",
        "uPinkOn", "uPink", "uWarm", "uTint",
        "uLipOn", "uLipAmt", "uLipColor",
        "uBrowOn", "uBrowAmt", "uBrowColor",
        "uBlushOn", "uBlushAmt", "uBlushColor", "uBlushSize", "uCheekL", "uCheekR", "uImgSize",
        "uMask2",
        "uShadowOn", "uShadowAmt", "uShadowColor",
        "uLinerOn", "uLinerAmt", "uLinerColor",
        "uHiOn", "uHiAmt", "uHiColor", "uHiSize", "uHiF", "uHiN", "uHiC",
        "uConOn", "uConAmt", "uConColor", "uConSize", "uConL", "uConR",
        "uUnderOn", "uUnderAmt", "uUnderSize", "uUnderL", "uUnderR",
        "uTeethOn", "uTeethAmt",
        "uMask3", "uLashOn", "uLashAmt", "uLashColor",
        "uDebug",
    )

    /**
     * Where each FilterParams key is consumed: a GLSL uniform name, or "FaceMaskBuilder" for mask
     * parameters applied on the CPU when drawing the mask. Unit tests enforce this covers every param.
     */
    val PARAM_TARGETS: Map<String, String> = mapOf(
        "smoothness" to "uSmooth", "radius" to "uRadius", "edgePreserve" to "uEdge",
        "brightness" to "uBright", "contrast" to "uContrast", "saturation" to "uSat",
        "warmth" to "uWarm", "tint" to "uTint", "pink" to "uPink",
        "lipstickAmount" to "uLipAmt", "browAmount" to "uBrowAmt", "blushAmount" to "uBlushAmt", "blushSize" to "uBlushSize",
        "feather" to "FaceMaskBuilder", "expand" to "FaceMaskBuilder", "eyeProtect" to "FaceMaskBuilder", "lipProtect" to "FaceMaskBuilder",
        "intensity" to "uIntensity",
        "lipstickColorHue" to "uLipColor", "lipstickColorSat" to "uLipColor", "lipstickColorVal" to "uLipColor",
        "browColorHue" to "uBrowColor", "browColorSat" to "uBrowColor", "browColorVal" to "uBrowColor",
        "blushColorHue" to "uBlushColor", "blushColorSat" to "uBlushColor", "blushColorVal" to "uBlushColor",
        "eyeshadowAmount" to "uShadowAmt", "eyeshadowSize" to "FaceMaskBuilder",
        "eyelinerAmount" to "uLinerAmt", "eyelinerWidth" to "FaceMaskBuilder", "eyelinerWing" to "FaceMaskBuilder", "eyelinerWingAngle" to "FaceMaskBuilder",
        "highlightAmount" to "uHiAmt", "highlightSize" to "uHiSize",
        "contourAmount" to "uConAmt", "contourSize" to "uConSize",
        "underEyeAmount" to "uUnderAmt", "underEyeSize" to "uUnderSize",
        "teethAmount" to "uTeethAmt",
        "eyelashAmount" to "uLashAmt", "eyelashLength" to "FaceMaskBuilder", "eyelashLower" to "FaceMaskBuilder",
        "eyelashColorHue" to "uLashColor", "eyelashColorSat" to "uLashColor", "eyelashColorVal" to "uLashColor",
        "eyeshadowColorHue" to "uShadowColor", "eyeshadowColorSat" to "uShadowColor", "eyeshadowColorVal" to "uShadowColor",
        "eyelinerColorHue" to "uLinerColor", "eyelinerColorSat" to "uLinerColor", "eyelinerColorVal" to "uLinerColor",
        "highlightColorHue" to "uHiColor", "highlightColorSat" to "uHiColor", "highlightColorVal" to "uHiColor",
        "contourColorHue" to "uConColor", "contourColorSat" to "uConColor", "contourColorVal" to "uConColor",
    )
    /** On/off uniform per switchable group key. */
    val GROUP_TARGETS: Map<String, String> = mapOf(
        "smooth" to "uSmoothOn", "bright" to "uBrightOn", "pink" to "uPinkOn",
        "lipstick" to "uLipOn", "brow" to "uBrowOn", "blush" to "uBlushOn",
        "eyeshadow" to "uShadowOn", "eyeliner" to "uLinerOn", "highlight" to "uHiOn", "contour" to "uConOn",
        "undereye" to "uUnderOn", "teeth" to "uTeethOn", "eyelash" to "uLashOn",
    )

    val VERTEX = """
attribute vec2 aPos;
uniform vec2 uCrop;
uniform float uMirror;
varying vec2 vUv;
void main() {
    vec2 uv = aPos * 0.5 + 0.5;
    uv.y = 1.0 - uv.y;
    if (uMirror > 0.5) uv.x = 1.0 - uv.x;
    vUv = (uv - 0.5) * uCrop + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
""".trimStart()

    val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vUv;
uniform sampler2D uTex;
uniform sampler2D uMask;
uniform vec2 uTexel;
uniform float uBypass;
uniform float uIntensity;
uniform float uSmoothOn;
uniform float uSmooth;
uniform float uRadius;
uniform float uEdge;
uniform float uBrightOn;
uniform float uBright;
uniform float uContrast;
uniform float uSat;
uniform float uPinkOn;
uniform float uPink;
uniform float uWarm;
uniform float uTint;
uniform float uLipOn;
uniform float uLipAmt;
uniform vec3 uLipColor;
uniform float uBrowOn;
uniform float uBrowAmt;
uniform vec3 uBrowColor;
uniform float uBlushOn;
uniform float uBlushAmt;
uniform vec3 uBlushColor;
uniform float uBlushSize;
uniform vec2 uCheekL;
uniform vec2 uCheekR;
uniform vec2 uImgSize;
uniform sampler2D uMask2;
uniform float uShadowOn;
uniform float uShadowAmt;
uniform vec3 uShadowColor;
uniform float uLinerOn;
uniform float uLinerAmt;
uniform vec3 uLinerColor;
uniform float uHiOn;
uniform float uHiAmt;
uniform vec3 uHiColor;
uniform float uHiSize;
uniform vec2 uHiF;
uniform vec2 uHiN;
uniform vec2 uHiC;
uniform float uConOn;
uniform float uConAmt;
uniform vec3 uConColor;
uniform float uConSize;
uniform vec2 uConL;
uniform vec2 uConR;
uniform float uUnderOn;
uniform float uUnderAmt;
uniform float uUnderSize;
uniform vec2 uUnderL;
uniform vec2 uUnderR;
uniform float uTeethOn;
uniform float uTeethAmt;
uniform sampler2D uMask3;
uniform float uLashOn;
uniform float uLashAmt;
uniform vec3 uLashColor;
// diagnostics: 0 normal, 1 passthrough (camera texture only), 2 show mask1 RGB, 3 show mask2 RGB, 4 show mask3 RGB
uniform float uDebug;

// camera sample in upright, NOT mirrored image UV (y down). The OES variant replaces this function.
vec4 camTex(vec2 uv) {
    return texture2D(uTex, uv);
}

// anisotropic gaussian around centre c (UV), sigma s (px), aspect = (x scale, y scale)
float blob(vec2 c, float s, vec2 aspect) {
    vec2 d = (vUv - c) * uImgSize / aspect;
    return exp(-dot(d, d) / (2.0 * s * s));
}

void main() {
    vec4 src = camTex(vUv);
    vec4 mk = texture2D(uMask, vUv);
    vec4 mk2 = texture2D(uMask2, vUv);
    vec4 mk3 = texture2D(uMask3, vUv);
    if (uDebug > 0.5) {
        if (uDebug < 1.5) gl_FragColor = vec4(src.rgb, 1.0);
        else if (uDebug < 2.5) gl_FragColor = vec4(mk.rgb, 1.0);
        else if (uDebug < 3.5) gl_FragColor = vec4(mk2.rgb, 1.0);
        else gl_FragColor = vec4(mk3.rgb, 1.0);
        return;
    }
    float m = mk.r * uIntensity;
    float anyM = max(max(mk.r, max(mk.g, mk.b)), max(max(mk2.r, max(mk2.g, mk2.b)), max(mk3.r, mk3.g)));
    if (uBypass > 0.5 || anyM * uIntensity <= 0.002) {
        gl_FragColor = src;
        return;
    }
    vec3 c = src.rgb;
    // 1) skin smoothing: edge-preserving (bilateral) blur, 12 directions x 2 rings
    if (uSmoothOn > 0.5 && uSmooth > 0.0) {
        float sigma = mix(0.40, 0.03, uEdge);
        float inv = 1.0 / (2.0 * sigma * sigma);
        vec3 sum = c;
        float wsum = 1.0;
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 0.5235988;
            vec2 dir = vec2(cos(a), sin(a)) * uTexel * uRadius;
            for (int r = 1; r <= 2; r++) {
                vec3 s = camTex(vUv + dir * (float(r) * 0.5)).rgb;
                vec3 d = s - src.rgb;
                float w = exp(-dot(d, d) * inv);
                sum += s * w;
                wsum += w;
            }
        }
        c = mix(c, sum / wsum, uSmooth);
    }
    // 2) brightening: brightness, contrast, saturation
    if (uBrightOn > 0.5) {
        c = c + vec3(uBright);
        c = (c - 0.5) * uContrast + 0.5;
        float l = dot(c, vec3(0.299, 0.587, 0.114));
        c = mix(vec3(l), c, uSat);
    }
    // 3) rosy skin: pink amount, warmth, tint
    if (uPinkOn > 0.5) {
        vec3 rosy = c * vec3(1.0, 0.90, 0.94) + vec3(0.06, 0.0, 0.03);
        c = mix(c, rosy, uPink);
        c.r += 0.08 * uWarm;
        c.b -= 0.08 * uWarm;
        c.g -= 0.06 * uTint;
    }
    c = clamp(c, 0.0, 1.0);
    vec3 outc = mix(src.rgb, c, m);
    float lum = dot(outc, vec3(0.299, 0.587, 0.114));
    // 4) blush: soft gaussian on both cheeks, inside the skin mask
    if (uBlushOn > 0.5 && uBlushAmt > 0.0) {
        vec2 p = vUv * uImgSize;
        vec2 dl = p - uCheekL * uImgSize;
        vec2 dr = p - uCheekR * uImgSize;
        float s2 = 2.0 * uBlushSize * uBlushSize;
        float w = (exp(-dot(dl, dl) / s2) + exp(-dot(dr, dr) / s2)) * mk.r;
        vec3 bc = uBlushColor;
        outc = mix(outc, outc * bc + 0.04 * bc, clamp(w * uBlushAmt * uIntensity, 0.0, 1.0) * 0.6);
    }
    // 4b) contour: soft shading under the cheekbones (multiply toward the contour colour), skin only
    if (uConOn > 0.5 && uConAmt > 0.0) {
        float w = (blob(uConL, uConSize, vec2(1.0, 1.8)) + blob(uConR, uConSize, vec2(1.0, 1.8))) * mk.r;
        outc = mix(outc, outc * (0.45 + uConColor), clamp(w * uConAmt * uIntensity, 0.0, 1.0) * 0.55);
    }
    // 4c) highlight: screen-blend glow on forehead, nose bridge (tall) and chin, skin only
    if (uHiOn > 0.5 && uHiAmt > 0.0) {
        float w = (blob(uHiF, uHiSize * 1.3, vec2(1.6, 1.0)) + blob(uHiN, uHiSize * 0.6, vec2(0.6, 2.2)) + blob(uHiC, uHiSize * 0.8, vec2(1.3, 1.0))) * mk.r;
        vec3 scr = 1.0 - (1.0 - outc) * (1.0 - uHiColor * 0.5);
        outc = mix(outc, scr, clamp(w * uHiAmt * uIntensity, 0.0, 1.0));
    }
    // 4d) under-eye brightening: lift shadows below each eye (wide, flat ellipse), skin only
    if (uUnderOn > 0.5 && uUnderAmt > 0.0) {
        float w = (blob(uUnderL, uUnderSize, vec2(1.8, 0.8)) + blob(uUnderR, uUnderSize, vec2(1.8, 0.8))) * max(mk.r, 0.6);
        vec3 lifted = outc + (1.0 - outc) * 0.3 + vec3(0.015, 0.008, 0.0);
        outc = mix(outc, lifted, clamp(w * uUnderAmt * uIntensity, 0.0, 1.0) * 0.7);
    }
    // 4e) eyeshadow: tinted multiply on the lid (mask2.r)
    if (uShadowOn > 0.5 && uShadowAmt > 0.0) {
        outc = mix(outc, outc * (0.35 + 0.9 * uShadowColor), clamp(mk2.r * uShadowAmt * uIntensity, 0.0, 1.0) * 0.8);
    }
    // 4f) eyeliner: paint the liner colour along the lash line (mask2.g)
    if (uLinerOn > 0.5 && uLinerAmt > 0.0) {
        outc = mix(outc, min(outc, uLinerColor + 0.1 * outc), clamp(mk2.g * uLinerAmt * uIntensity, 0.0, 1.0));
    }
    // 4f2) eyelashes: thin dark strokes from the lash lines (mask3.r upper, mask3.g lower scaled by eyelashLower)
    if (uLashOn > 0.5 && uLashAmt > 0.0) {
        float w = clamp(max(mk3.r, mk3.g) * uLashAmt * uIntensity * 1.3, 0.0, 1.0);
        outc = mix(outc, min(outc, uLashColor + 0.06 * outc), w);
    }
    // 4g) teeth whitening: inside the mouth opening (mask2.b), only bright + low-saturation pixels
    if (uTeethOn > 0.5 && uTeethAmt > 0.0) {
        float l = dot(outc, vec3(0.299, 0.587, 0.114));
        float sat = max(outc.r, max(outc.g, outc.b)) - min(outc.r, min(outc.g, outc.b));
        float tooth = smoothstep(0.28, 0.5, l) * (1.0 - smoothstep(0.25, 0.5, sat));
        vec3 white = mix(vec3(l), outc, 0.35) * 1.12 + vec3(0.0, 0.01, 0.03);
        outc = mix(outc, white, clamp(mk2.b * tooth * uTeethAmt * uIntensity, 0.0, 1.0));
    }
    // 5) lipstick: colour keeps the lip's own shading (luminance), G channel = lips
    if (uLipOn > 0.5 && uLipAmt > 0.0) {
        vec3 lc = uLipColor;
        vec3 tinted = lc * (0.35 + 1.1 * lum);
        outc = mix(outc, tinted, clamp(mk.g * uLipAmt * uIntensity, 0.0, 1.0));
    }
    // 6) brow fill: darken toward a brown, B channel = brows
    if (uBrowOn > 0.5 && uBrowAmt > 0.0) {
        vec3 brow = uBrowColor;
        outc = mix(outc, min(outc, brow + 0.25 * outc), clamp(mk.b * uBrowAmt * uIntensity, 0.0, 1.0));
    }
    gl_FragColor = vec4(clamp(outc, 0.0, 1.0), src.a);
}
""".trimStart()

    /** Extra uniforms of [FRAGMENT_OES] only (camera as GL_TEXTURE_EXTERNAL_OES from a CameraX Preview SurfaceTexture). */
    val OES_UNIFORMS = listOf("uSTM", "uCamRot", "uCamFlip")

    /**
     * Same effects as [FRAGMENT] but the camera comes from a SurfaceTexture (samplerExternalOES):
     * upright UV -> (un-mirror) -> buffer UV (undo uCamRot) -> GL texcoord -> SurfaceTexture matrix.
     * When the Surface carries the camera transform, the SurfaceTexture matrix already rotates to upright and
     * mirrors the front camera: then uCamRot = 0 and uCamFlip = 1 undoes that mirroring.
     */
    val FRAGMENT_OES: String = run {
        val src = FRAGMENT
        val a = src.indexOf("vec4 camTex(vec2 uv) {"); val b = src.indexOf("}", a) + 1
        "#extension GL_OES_EGL_image_external : require\n" +
            src.substring(0, a).replace("uniform sampler2D uTex;", "uniform samplerExternalOES uTex;\nuniform mat4 uSTM;\nuniform float uCamRot;\nuniform float uCamFlip;") +
            """vec4 camTex(vec2 u) {
    vec2 v = u;
    if (uCamFlip > 0.5) v.x = 1.0 - v.x;                   // buffer is mirrored by the camera transform
    vec2 s = v;                                            // rotation 0
    if (uCamRot > 45.0 && uCamRot < 135.0) s = vec2(v.y, 1.0 - v.x);        // 90
    else if (uCamRot >= 135.0 && uCamRot < 225.0) s = vec2(1.0 - v.x, 1.0 - v.y); // 180
    else if (uCamRot >= 225.0) s = vec2(1.0 - v.y, v.x);  // 270
    vec4 t = uSTM * vec4(s.x, 1.0 - s.y, 0.0, 1.0);        // y-down -> GL texcoord, then SurfaceTexture transform
    return texture2D(uTex, t.xy);
}""" + src.substring(b)
    }
}
