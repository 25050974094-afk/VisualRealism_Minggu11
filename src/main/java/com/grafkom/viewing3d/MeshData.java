package com.grafkom.viewing3d;

/**
 * Data mesh murni (tanpa OpenGL): vertex interleaved [pos3 | normal3 | uv2] + indeks segitiga.
 */
public final class MeshData {
    public static final int FLOATS_PER_VERTEX = 8;

    public final float[] vertices;
    public final int[] indices;

    public MeshData(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public int vertexCount() {
        return vertices.length / FLOATS_PER_VERTEX;
    }
}
