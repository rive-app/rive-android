#pragma once

#include <GLES3/gl3.h>
#include "glm/glm.hpp"
#include "glm/gtc/matrix_transform.hpp"
#include "glm/gtc/type_ptr.hpp"

struct Triangle
{
  GLuint vbo, vao;
  GLuint shaderProgram;
  float rotation;
  float points[9];

  Triangle() : vbo(0),
               vao(0),
               shaderProgram(0),
               rotation(0.0f),
               points{
                   0.0f, 0.5f, 0.0f,
                   0.5f, -0.5f, 0.0f,
                   -0.5f, -0.5f, 0.0f} {};

  void init()
  {
    // Set up VBO
    glGenBuffers(1, &vbo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, 9 * sizeof(float), points, GL_STATIC_DRAW);

    // Set up VAO
    glGenVertexArrays(1, &vao);
    glBindVertexArray(vao);
    glEnableVertexAttribArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, NULL);

    const char *vertexShader =
        "#version 300 es\n"
        "in vec3 vp;"
        "uniform mat4 transform;"
        "void main() {"
        "  gl_Position = transform * vec4(vp, 1.0);"
        "}";

    const char *fragmentShader =
        "#version 300 es\n"
        "out vec4 fragColor;"
        "void main() {"
        "  fragColor = vec4(0.5, 0.3, 0.5, 1.0);"
        "}";

    GLuint vs = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vs, 1, &vertexShader, NULL);
    glCompileShader(vs);

    GLuint fs = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fs, 1, &fragmentShader, NULL);
    glCompileShader(fs);

    shaderProgram = glCreateProgram();
    glAttachShader(shaderProgram, fs);
    glAttachShader(shaderProgram, vs);
    glLinkProgram(shaderProgram);
  }

  void draw(float elapsedTime)
  {
    glUseProgram(shaderProgram);

    glm::mat4 transform = glm::mat4(1.0f); // make sure to initialize matrix to identity matrix first
    transform = glm::translate(transform, glm::vec3(0.5f, -0.5f, 0.0f));
    rotation += elapsedTime;
    transform = glm::rotate(transform, rotation, glm::vec3(0.0f, 0.0f, 1.0f));

    unsigned int transformLoc = glGetUniformLocation(shaderProgram, "transform");
    glUniformMatrix4fv(transformLoc, 1, GL_FALSE, glm::value_ptr(transform));

    glBindVertexArray(vao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
  }
};
