package com.grafkom.viewing3d;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

/**
 * 3D Visual Realism Suite (Minggu 11): bola bertekstur di atas lantai dengan
 * Phong/Blinn-Phong, flat/smooth shading, Z-Buffer, back-face culling,
 * filter tekstur dan shadow mapping dua tahap.
 */
public class VisualRealismApp {

    // Jumlah segmen bola sengaja kecil agar facet flat shading mudah terlihat.
    private static final int SPHERE_SLICES = 16;
    private static final int SPHERE_STACKS = 12;
    private static final float SPHERE_RADIUS = 1.0f;
    private static final Vector3f SPHERE_CENTER = new Vector3f(0f, 1.25f, 0f);
    private static final float FLOOR_HALF = 10f;

    private static final Vector3f DEFAULT_LIGHT = new Vector3f(3.5f, 4.2f, 0.5f);
    private static final float DEFAULT_BIAS = 0.0015f;
    private static final float MAX_BIAS = 0.02f;

    private long window;
    private int width = 1280;
    private int height = 720;

    // --- State mode (diubah lewat tombol) ---
    private boolean blinnPhong = true;
    private boolean smoothShading = true;
    private boolean depthTest = true;
    private boolean backFaceCulling = true;
    private boolean mipmapFilter = true;
    private boolean shadowsEnabled = true;
    private boolean vsync = true;
    private float shadowBias = DEFAULT_BIAS;
    private final Vector3f lightPos = new Vector3f(DEFAULT_LIGHT);

    private final Camera camera = new Camera();
    private boolean dragging = false;
    private double lastMouseX, lastMouseY;

    // --- Resource GPU ---
    private ShaderProgram phongShader;
    private ShaderProgram shadowShader;
    private Mesh sphereSmooth;
    private Mesh sphereFlat;
    private Mesh floorMesh;
    private Texture2D sphereTexture;
    private Texture2D floorTexture;
    private ShadowMapFBO shadowFBO;

    // --- FPS & judul ---
    private int fps = 0;
    private int frameCount = 0;
    private double fpsTimer = 0;
    private boolean titleDirty = true;

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f lightProjection = new Matrix4f();
    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightSpace = new Matrix4f();
    private final Matrix4f model = new Matrix4f();

    public void run() {
        try {
            init();
            loop();
        } finally {
            cleanup();
        }
    }

    // ------------------------------------------------------------------ init
    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Gagal menginisialisasi GLFW");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, "Visual Realism - Minggu 11", NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Gagal membuat jendela GLFW (butuh OpenGL 3.3)");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        setupCallbacks();

        phongShader = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        shadowShader = new ShaderProgram("/shaders/shadow.vert", "/shaders/shadow.frag");

        MeshData[] sphere = MeshBuilder.sphere(SPHERE_RADIUS, SPHERE_SLICES, SPHERE_STACKS);
        sphereSmooth = new Mesh(sphere[0]);
        sphereFlat = new Mesh(sphere[1]);
        floorMesh = new Mesh(MeshBuilder.floor(FLOOR_HALF, 6f));

        sphereTexture = new Texture2D("/textures/bola.png");
        floorTexture = new Texture2D("/textures/lantai.png");
        shadowFBO = new ShadowMapFBO();

        applyPreset(1);
        printHelp();
        glfwShowWindow(window);
    }

    private void setupCallbacks() {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) { // hanya satu kali per penekanan (REPEAT diabaikan)
                switch (key) {
                    case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(win, true);
                    case GLFW_KEY_B -> blinnPhong = !blinnPhong;
                    case GLFW_KEY_F -> smoothShading = !smoothShading;
                    case GLFW_KEY_Z -> depthTest = !depthTest;
                    case GLFW_KEY_C -> backFaceCulling = !backFaceCulling;
                    case GLFW_KEY_T -> toggleTextureFilter();
                    case GLFW_KEY_H -> shadowsEnabled = !shadowsEnabled;
                    case GLFW_KEY_V -> {
                        vsync = !vsync;
                        glfwSwapInterval(vsync ? 1 : 0);
                    }
                    case GLFW_KEY_R -> {
                        applyPreset(1);
                        lightPos.set(DEFAULT_LIGHT);
                        shadowBias = DEFAULT_BIAS;
                    }
                    case GLFW_KEY_1 -> applyPreset(1);
                    case GLFW_KEY_2 -> applyPreset(2);
                    case GLFW_KEY_3 -> applyPreset(3);
                    case GLFW_KEY_4 -> applyPreset(4);
                    default -> { }
                }
                titleDirty = true;
            }
            // Bias boleh berulang saat ditahan (bukan pergantian mode). Shift = langkah besar.
            if ((action == GLFW_PRESS || action == GLFW_REPEAT)
                    && (key == GLFW_KEY_N || key == GLFW_KEY_M)) {
                float step = (mods & GLFW_MOD_SHIFT) != 0 ? 0.0005f : 0.0001f;
                float b = shadowBias + (key == GLFW_KEY_M ? step : -step);
                b = Math.max(0f, Math.min(MAX_BIAS, b));
                shadowBias = Math.round(b * 10000f) / 10000f;
                titleDirty = true;
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                dragging = action == GLFW_PRESS;
                if (dragging) {
                    double[] x = new double[1];
                    double[] y = new double[1];
                    glfwGetCursorPos(win, x, y);
                    lastMouseX = x[0];
                    lastMouseY = y[0];
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, x, y) -> {
            if (dragging) {
                float dx = (float) (x - lastMouseX);
                float dy = (float) (y - lastMouseY);
                camera.rotate(dx * 0.15f, -dy * 0.15f);
            }
            lastMouseX = x;
            lastMouseY = y;
        });

        glfwSetScrollCallback(window, (win, xoff, yoff) -> camera.moveForward((float) yoff * 0.5f));
    }

    private void toggleTextureFilter() {
        mipmapFilter = !mipmapFilter;
        sphereTexture.setFilter(mipmapFilter);
        floorTexture.setFilter(mipmapFilter);
    }

    /** Preset sudut pandang untuk pengujian tiap indikator. */
    private void applyPreset(int n) {
        Vector3f eye;
        Vector3f target = new Vector3f(SPHERE_CENTER);
        switch (n) {
            case 2 -> eye = new Vector3f(0f, -2.0f, 6.5f);   // dari bawah lantai (culling)
            case 3 -> {
                eye = new Vector3f(0f, 0.5f, 9.0f);          // sudut rendah (mipmap & bayangan)
                target.set(0f, 0.8f, 0f);
            }
            case 4 -> eye = new Vector3f(1.2f, 1.6f, 3.2f);  // dekat bola (facet, tekstur, kilauan)
            default -> eye = new Vector3f(0f, 2.2f, 6.5f);   // tampilan utama
        }
        camera.lookAt(eye, target);
    }

    private void printHelp() {
        System.out.println("=== Visual Realism - Minggu 11 ===");
        System.out.println("B: Phong/Blinn-Phong   F: Flat/Smooth   Z: Depth test   C: Back-face culling");
        System.out.println("T: Nearest/Bilinear+Mipmap   H: Bayangan on/off   V: VSync");
        System.out.println("Panah: geser lampu X/Z   PageUp/PageDown: tinggi lampu");
        System.out.println("N/M: kurangi/tambah shadow bias (Shift = langkah besar)");
        System.out.println("W/A/S/D/Q/E + drag mouse kiri + scroll: kamera   1-4: sudut pandang   R: reset   Esc: keluar");
    }

    // ------------------------------------------------------------------ loop
    private void loop() {
        double last = glfwGetTime();
        fpsTimer = last;
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - last);
            last = now;

            glfwPollEvents();
            processHeldKeys(dt);
            render();
            glfwSwapBuffers(window);

            frameCount++;
            if (now - fpsTimer >= 0.5) {
                fps = (int) Math.round(frameCount / (now - fpsTimer));
                frameCount = 0;
                fpsTimer = now;
                titleDirty = true;
            }
            if (titleDirty) {
                updateTitle();
                titleDirty = false;
            }
        }
    }

    private void processHeldKeys(float dt) {
        float speed = 4f * dt * (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS ? 3f : 1f);
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) camera.moveForward(speed);
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) camera.moveForward(-speed);
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) camera.moveRight(speed);
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) camera.moveRight(-speed);
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) camera.moveUp(speed);
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) camera.moveUp(-speed);

        float ls = 4f * dt;
        boolean moved = false;
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) { lightPos.x -= ls; moved = true; }
        if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) { lightPos.x += ls; moved = true; }
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) { lightPos.z -= ls; moved = true; }
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) { lightPos.z += ls; moved = true; }
        if (glfwGetKey(window, GLFW_KEY_PAGE_UP) == GLFW_PRESS) { lightPos.y += ls; moved = true; }
        if (glfwGetKey(window, GLFW_KEY_PAGE_DOWN) == GLFW_PRESS) { lightPos.y -= ls; moved = true; }
        if (moved) {
            lightPos.x = Math.max(-8f, Math.min(8f, lightPos.x));
            lightPos.z = Math.max(-8f, Math.min(8f, lightPos.z));
            lightPos.y = Math.max(1.5f, Math.min(10f, lightPos.y));
        }
    }

    private void updateTitle() {
        String title = String.format(Locale.US,
                "VisualRealism M11 | %s | %s | Z-Buffer:%s Culling:%s | Tekstur:%s | Bayangan:%s Bias:%.4f | VSync:%s | FPS:%d",
                blinnPhong ? "Blinn-Phong" : "Phong",
                smoothShading ? "Smooth" : "Flat",
                depthTest ? "ON" : "OFF",
                backFaceCulling ? "ON" : "OFF",
                mipmapFilter ? "Bilinear+Mipmap" : "Nearest",
                shadowsEnabled ? "ON" : "OFF",
                shadowBias,
                vsync ? "ON" : "OFF",
                fps);
        glfwSetWindowTitle(window, title);
    }

    // ---------------------------------------------------------------- render
    private void render() {
        int[] fw = new int[1];
        int[] fh = new int[1];
        glfwGetFramebufferSize(window, fw, fh);
        width = fw[0];
        height = fh[0];
        if (width == 0 || height == 0) {
            return; // jendela di-minimize
        }

        Mesh sphere = smoothShading ? sphereSmooth : sphereFlat;

        // Matriks cahaya (proyeksi ortografik, melihat ke pusat bola)
        Vector3f dir = new Vector3f(SPHERE_CENTER).sub(lightPos).normalize();
        Vector3f up = Math.abs(dir.y) > 0.99f ? new Vector3f(0f, 0f, 1f) : new Vector3f(0f, 1f, 0f);
        lightProjection.setOrtho(-8f, 8f, -8f, 8f, 0.5f, 35f);
        lightView.setLookAt(lightPos, SPHERE_CENTER, up);
        lightSpace.set(lightProjection).mul(lightView);

        // ------------- PASS 1: depth map dari sudut pandang lampu -------------
        if (shadowsEnabled) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
            glDisable(GL_CULL_FACE); // pass bayangan tidak boleh terpengaruh toggle Z/C
            shadowFBO.bindForWriting();
            shadowShader.use();
            shadowShader.setMat4("lightSpaceMatrix", lightSpace);

            sphereModel(model);
            shadowShader.setMat4("model", model);
            sphere.draw();

            model.identity();
            shadowShader.setMat4("model", model);
            floorMesh.draw();

            shadowFBO.unbind(width, height);
        }

        // ------------- PASS 2: render normal dari kamera -------------
        glClearColor(0.13f, 0.15f, 0.21f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        HSRManager.setupDepthTest(depthTest);
        HSRManager.setupBackFaceCulling(backFaceCulling);

        projection.setPerspective((float) Math.toRadians(45.0), (float) width / height, 0.1f, 100f);
        camera.getViewMatrix(view);

        phongShader.use();
        phongShader.setMat4("projection", projection);
        phongShader.setMat4("view", view);
        phongShader.setMat4("lightSpaceMatrix", lightSpace);
        phongShader.setVec3("lightPos", lightPos);
        phongShader.setVec3("viewPos", camera.getPosition());
        phongShader.setVec3("lightColor", 1f, 1f, 1f);
        phongShader.setBool("useBlinnPhong", blinnPhong);
        phongShader.setBool("shadowsEnabled", shadowsEnabled);
        phongShader.setFloat("shadowBias", shadowBias);
        phongShader.setInt("textureSampler", 0);
        phongShader.setInt("shadowMap", 1);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, shadowFBO.getDepthTextureID());

        // Urutan gambar: bola dulu, lantai sesudahnya. Saat Z-Buffer dimatikan,
        // lantai akan menimpa bola sehingga efek depth test terlihat jelas.
        phongShader.setBool("unlit", false);
        phongShader.setBool("useTexture", true);

        sphereModel(model);
        phongShader.setMat4("model", model);
        phongShader.setFloat("specularStrength", 0.8f);
        sphereTexture.bind(0);
        sphere.draw();

        model.identity();
        phongShader.setMat4("model", model);
        phongShader.setFloat("specularStrength", 0.25f);
        floorTexture.bind(0);
        floorMesh.draw();

        // Penanda posisi lampu (tanpa pencahayaan)
        phongShader.setBool("unlit", true);
        phongShader.setBool("useTexture", false);
        phongShader.setVec3("objectColor", 1f, 0.95f, 0.55f);
        model.identity().translate(lightPos).scale(0.12f);
        phongShader.setMat4("model", model);
        sphereSmooth.draw();
    }

    private void sphereModel(Matrix4f dest) {
        dest.identity().translate(SPHERE_CENTER);
    }

    // --------------------------------------------------------------- cleanup
    private void cleanup() {
        if (window != NULL) {
            glfwDestroyWindow(window);
        }
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }
}
