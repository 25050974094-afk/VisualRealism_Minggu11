package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL33.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Membungkus program GLSL 330 core. Shader dimuat dari classpath (src/main/resources). */
public class ShaderProgram {
    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();
    private final float[] matrixBuffer = new float[16];

    public ShaderProgram(String vertexResource, String fragmentResource) {
        int vs = compile(GL_VERTEX_SHADER, readResource(vertexResource), vertexResource);
        int fs = compile(GL_FRAGMENT_SHADER, readResource(fragmentResource), fragmentResource);
        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Link shader gagal: " + glGetProgramInfoLog(programId));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static String readResource(String path) {
        try (InputStream in = ShaderProgram.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource shader tidak ditemukan: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca shader: " + path, e);
        }
    }

    private static int compile(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Compile shader gagal (" + name + "): " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    public void use() {
        glUseProgram(programId);
    }

    private int location(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setInt(String name, int value) {
        glUniform1i(location(name), value);
    }

    public void setBool(String name, boolean value) {
        glUniform1i(location(name), value ? 1 : 0);
    }

    public void setFloat(String name, float value) {
        glUniform1f(location(name), value);
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(location(name), x, y, z);
    }

    public void setVec3(String name, Vector3f v) {
        glUniform3f(location(name), v.x, v.y, v.z);
    }

    public void setMat4(String name, Matrix4f m) {
        m.get(matrixBuffer);
        glUniformMatrix4fv(location(name), false, matrixBuffer);
    }

    public void delete() {
        glDeleteProgram(programId);
    }
}
