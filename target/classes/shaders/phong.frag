#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;
uniform sampler2D textureSampler;
uniform sampler2D shadowMap;
uniform bool useTexture;
uniform bool useBlinnPhong;
uniform bool unlit;
uniform bool shadowsEnabled;
uniform float specularStrength;
uniform float shadowBias;

// Shadow mapping pass 2: bandingkan kedalaman fragmen (sudut pandang lampu)
// dengan kedalaman tersimpan di shadow map. Mengembalikan 0.0 (terang) .. 1.0 (bayangan).
float calcShadow(vec4 fragPosLS, float NdotL)
{
    vec3 p = fragPosLS.xyz / fragPosLS.w;
    p = p * 0.5 + 0.5;
    if (p.z > 1.0 || p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0)
        return 0.0;

    // Bias dinamis berdasarkan kemiringan permukaan: max(10*b*(1-N.L), b)
    float bias = max(shadowBias * 10.0 * (1.0 - NdotL), shadowBias);

    // PCF 3x3 agar tepi bayangan halus
    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    float shadow = 0.0;
    for (int x = -1; x <= 1; ++x)
    {
        for (int y = -1; y <= 1; ++y)
        {
            float closest = texture(shadowMap, p.xy + vec2(x, y) * texel).r;
            shadow += (p.z - bias > closest) ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

void main()
{
    if (unlit)
    {
        FragColor = vec4(objectColor, 1.0);
        return;
    }

    vec3 norm = normalize(Normal);
    if (!gl_FrontFacing) norm = -norm; // sisi belakang (saat culling mati) dihitung terbalik

    // 1. Ambient
    float ambientStrength = 0.2;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse (Lambertian)
    vec3 lightDir = normalize(lightPos - FragPos);
    float NdotL = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = NdotL * lightColor;

    // 3. Specular (Phong vs Blinn-Phong)
    vec3 viewDir = normalize(viewPos - FragPos);
    float spec = 0.0;
    if (useBlinnPhong)
    {
        vec3 halfwayDir = normalize(lightDir + viewDir);
        spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);   // Blinn-Phong
    }
    else
    {
        vec3 reflectDir = reflect(-lightDir, norm);
        spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0); // Phong standar
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Bayangan
    float shadow = shadowsEnabled ? calcShadow(FragPosLightSpace, NdotL) : 0.0;
    float lit = mix(1.0, 0.2, shadow); // faktor bayangan 0.2 sesuai modul

    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb : objectColor;
    vec3 result = (ambient + lit * (diffuse + specular)) * baseColor;
    FragColor = vec4(result, 1.0);
}
