package com.grafkom.viewing3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Pembuat geometri prosedural (bola dan lantai). Tidak bergantung pada OpenGL.
 *
 * Bola dibuat dalam dua versi dari segitiga yang sama:
 *  - smooth : vertex dibagi bersama, normal per titik (arah dari pusat bola) yang
 *             diinterpolasi di fragment shader;
 *  - flat   : tiap segitiga punya 3 vertex sendiri dengan normal per bidang.
 */
public final class MeshBuilder {

    private MeshBuilder() {
    }

    /** @return array {smooth, flat} */
    public static MeshData[] sphere(float radius, int slices, int stacks) {
        int cols = slices + 1;
        int rows = stacks + 1;
        float[] pos = new float[rows * cols * 3];
        float[] uv = new float[rows * cols * 2];

        for (int i = 0; i < rows; i++) {
            double phi = Math.PI * i / stacks;
            float y = (float) (radius * Math.cos(phi));
            float ring = (float) (radius * Math.sin(phi));
            for (int j = 0; j < cols; j++) {
                double theta = 2.0 * Math.PI * j / slices;
                int idx = i * cols + j;
                pos[idx * 3] = (float) (ring * Math.cos(theta));
                pos[idx * 3 + 1] = y;
                pos[idx * 3 + 2] = (float) (ring * Math.sin(theta));
                uv[idx * 2] = (float) j / slices;
                uv[idx * 2 + 1] = 1.0f - (float) i / stacks;
            }
        }

        // Kumpulkan segitiga (CCW dilihat dari luar), buang segitiga degenerate di kutub.
        List<int[]> tris = new ArrayList<>();
        List<float[]> faceNormals = new ArrayList<>();
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int a = i * cols + j;
                int b = a + cols;
                int c = a + 1;
                int d = b + 1;
                addTriangle(pos, a, b, d, tris, faceNormals);
                addTriangle(pos, a, d, c, tris, faceNormals);
            }
        }

        // --- Versi smooth: normal per titik = arah dari pusat ---
        float[] sv = new float[rows * cols * MeshData.FLOATS_PER_VERTEX];
        for (int v = 0; v < rows * cols; v++) {
            float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            int o = v * MeshData.FLOATS_PER_VERTEX;
            sv[o] = x;
            sv[o + 1] = y;
            sv[o + 2] = z;
            sv[o + 3] = x / len;
            sv[o + 4] = y / len;
            sv[o + 5] = z / len;
            sv[o + 6] = uv[v * 2];
            sv[o + 7] = uv[v * 2 + 1];
        }
        int[] si = new int[tris.size() * 3];
        for (int t = 0; t < tris.size(); t++) {
            int[] tr = tris.get(t);
            si[t * 3] = tr[0];
            si[t * 3 + 1] = tr[1];
            si[t * 3 + 2] = tr[2];
        }

        // --- Versi flat: vertex tidak dibagi, normal per bidang ---
        float[] fv = new float[tris.size() * 3 * MeshData.FLOATS_PER_VERTEX];
        int[] fi = new int[tris.size() * 3];
        for (int t = 0; t < tris.size(); t++) {
            int[] tr = tris.get(t);
            float[] n = faceNormals.get(t);
            for (int k = 0; k < 3; k++) {
                int v = tr[k];
                int o = (t * 3 + k) * MeshData.FLOATS_PER_VERTEX;
                fv[o] = pos[v * 3];
                fv[o + 1] = pos[v * 3 + 1];
                fv[o + 2] = pos[v * 3 + 2];
                fv[o + 3] = n[0];
                fv[o + 4] = n[1];
                fv[o + 5] = n[2];
                fv[o + 6] = uv[v * 2];
                fv[o + 7] = uv[v * 2 + 1];
                fi[t * 3 + k] = t * 3 + k;
            }
        }
        return new MeshData[]{new MeshData(sv, si), new MeshData(fv, fi)};
    }

    private static void addTriangle(float[] pos, int i0, int i1, int i2,
                                    List<int[]> tris, List<float[]> normals) {
        float ax = pos[i1 * 3] - pos[i0 * 3];
        float ay = pos[i1 * 3 + 1] - pos[i0 * 3 + 1];
        float az = pos[i1 * 3 + 2] - pos[i0 * 3 + 2];
        float bx = pos[i2 * 3] - pos[i0 * 3];
        float by = pos[i2 * 3 + 1] - pos[i0 * 3 + 1];
        float bz = pos[i2 * 3 + 2] - pos[i0 * 3 + 2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) {
            return; // segitiga degenerate (di kutub)
        }
        float cx = (pos[i0 * 3] + pos[i1 * 3] + pos[i2 * 3]) / 3f;
        float cy = (pos[i0 * 3 + 1] + pos[i1 * 3 + 1] + pos[i2 * 3 + 1]) / 3f;
        float cz = (pos[i0 * 3 + 2] + pos[i1 * 3 + 2] + pos[i2 * 3 + 2]) / 3f;
        if (nx * cx + ny * cy + nz * cz < 0f) { // pastikan menghadap keluar (CCW dari luar)
            int tmp = i1;
            i1 = i2;
            i2 = tmp;
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        tris.add(new int[]{i0, i1, i2});
        normals.add(new float[]{nx / len, ny / len, nz / len});
    }

    /** Lantai persegi di y = 0, normal ke atas, UV berulang uvRepeat kali. */
    public static MeshData floor(float half, float uvRepeat) {
        float[] v = {
                -half, 0f, -half, 0f, 1f, 0f, 0f, 0f,
                -half, 0f, half, 0f, 1f, 0f, 0f, uvRepeat,
                half, 0f, half, 0f, 1f, 0f, uvRepeat, uvRepeat,
                half, 0f, -half, 0f, 1f, 0f, uvRepeat, 0f
        };
        int[] i = {0, 1, 2, 0, 2, 3}; // CCW jika dilihat dari atas
        return new MeshData(v, i);
    }
}
