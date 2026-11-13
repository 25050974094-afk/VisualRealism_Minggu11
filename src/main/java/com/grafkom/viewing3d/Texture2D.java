package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;

/**
 * Tekstur 2D yang dimuat lewat STBImage dari classpath (src/main/resources),
 * sehingga tetap ditemukan di folder mana pun proyek diekstrak.
 */
public class Texture2D {
    private final int textureID;

    public Texture2D(String resourcePath) {
        ByteBuffer fileData = readResource(resourcePath);
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        stbi_set_flip_vertically_on_load(true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer data = stbi_load_from_memory(fileData, width, height, channels, 4);
            if (data == null) {
                throw new IllegalStateException("Gagal memuat tekstur " + resourcePath + ": " + stbi_failure_reason());
            }
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width.get(0), height.get(0), 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, data);
            glGenerateMipmap(GL_TEXTURE_2D);
            stbi_image_free(data);
        } finally {
            memFree(fileData);
        }
        setFilter(true);
    }

    private static ByteBuffer readResource(String path) {
        try (InputStream in = Texture2D.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource tekstur tidak ditemukan: " + path);
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca tekstur: " + path, e);
        }
    }

    /** true = bilinear + mipmap (LINEAR_MIPMAP_LINEAR / LINEAR); false = nearest. */
    public void setFilter(boolean bilinearMipmap) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        if (bilinearMipmap) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    public void delete() {
        glDeleteTextures(textureID);
    }
}
