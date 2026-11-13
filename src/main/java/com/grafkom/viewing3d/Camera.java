package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Kamera bebas (fly camera): posisi + yaw/pitch. */
public class Camera {
    private final Vector3f position = new Vector3f(0f, 2.2f, 6.5f);
    private float yaw = -90f;   // derajat, -90 = menghadap -Z
    private float pitch = 0f;   // derajat

    /** Letakkan kamera di eye dan arahkan ke target. */
    public void lookAt(Vector3f eye, Vector3f target) {
        position.set(eye);
        Vector3f d = new Vector3f(target).sub(eye).normalize();
        pitch = (float) Math.toDegrees(Math.asin(d.y));
        yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x));
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getFront() {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vector3f((float) (Math.cos(y) * Math.cos(p)),
                (float) Math.sin(p),
                (float) (Math.sin(y) * Math.cos(p)));
    }

    public Matrix4f getViewMatrix(Matrix4f dest) {
        Vector3f f = getFront();
        return dest.setLookAt(position.x, position.y, position.z,
                position.x + f.x, position.y + f.y, position.z + f.z,
                0f, 1f, 0f);
    }

    public void moveForward(float distance) {
        position.fma(distance, getFront());
    }

    public void moveRight(float distance) {
        Vector3f right = getFront().cross(0f, 1f, 0f).normalize();
        position.fma(distance, right);
    }

    public void moveUp(float distance) {
        position.y += distance;
    }

    public void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;
        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;
    }
}
