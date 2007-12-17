/*
 *                      Nividic development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the microarray platform
 * of the École Normale Supérieure and the individual authors.
 * These should be listed in @author doc comments.
 *
 * For more information on the Nividic project and its aims,
 * or to join the Nividic mailing list, visit the home page
 * at:
 *
 *      http://www.transcriptome.ens.fr/nividic
 *
 */

package fr.ens.transcriptome.corsen.gui.qt;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;

import com.sun.opengl.util.GLUT;
import com.trolltech.qt.core.QPoint;
import com.trolltech.qt.core.QRect;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QMouseEvent;
import com.trolltech.qt.gui.QWheelEvent;
import com.trolltech.qt.opengl.QGLWidget;

public class TestGL extends QGLWidget {

  private GL gl;
  private GLU glu;
  private GLUT glut;

  private int xRot = 0;

  private int yRot = 0;

  private int zRot = 0;

  private double zoom = 1; // 0.5;

  private static final double ZOOM_FACTOR = 1.2;

  private static final double MIN_ZOOM = 0.1;

  private static final double MAX_ZOOM = 2;

  public static final int STACKS = 20;
  private static final float LEN = 0.5f;
  private boolean remakeObject = true;

  QPoint lastPos;

  private int gllist = -1;

  /**
   * Test if the particles can be drawed
   * @return Returns the remakeObject
   */
  public boolean isRemakeObject() {
    return remakeObject;
  }

  public void initializeGL() {

    GLContext context =
        GLDrawableFactory.getFactory().createExternalGLContext();

    this.gl = context.getGL();

    context.makeCurrent();
    this.glu = new GLU();
    this.glut = new GLUT();

    FloatBuffer mat_specular = FloatBuffer.wrap(new float[] {1, 1, 1, 1});
    FloatBuffer mat_shininess = FloatBuffer.wrap(new float[] {50});

    FloatBuffer white_light = FloatBuffer.wrap(new float[] {1, 1, 1, 1});
    FloatBuffer lmodel_ambient =
        FloatBuffer.wrap(new float[] {0.9f, 0.9f, 0.9f, 1.0f});

    // FloatBuffer light_ambient = FloatBuffer.wrap(new float[] {.9f, .9f, .9f,
    // 1});
    FloatBuffer light_ambient =
        FloatBuffer.wrap(new float[] {.3f, .3f, .3f, 3f});
    FloatBuffer light_diffuse = FloatBuffer.wrap(new float[] {1, 1, 1, 1});
    FloatBuffer light_specular = FloatBuffer.wrap(new float[] {1, 1, 1, 1});
    FloatBuffer light_position0 = FloatBuffer.wrap(new float[] {1, 1, 1, 0});
    FloatBuffer light_position1 = FloatBuffer.wrap(new float[] {-1, -1, -1, 0});

    gl.glClearColor(0, 0, 0, 0);
    gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, mat_specular);
    gl.glMaterialfv(GL.GL_FRONT, GL.GL_SHININESS, mat_shininess);
    gl.glLightfv(GL.GL_LIGHT0, GL.GL_POSITION, light_position0);
    gl.glLightfv(GL.GL_LIGHT0, GL.GL_DIFFUSE, white_light);
    gl.glLightfv(GL.GL_LIGHT0, GL.GL_SPECULAR, white_light);

    gl.glLightfv(GL.GL_LIGHT0, GL.GL_AMBIENT, light_ambient);
    gl.glLightfv(GL.GL_LIGHT0, GL.GL_DIFFUSE, light_diffuse);
    gl.glLightfv(GL.GL_LIGHT0, GL.GL_SPECULAR, light_specular);
    gl.glLightfv(GL.GL_LIGHT0, GL.GL_POSITION, light_position0);

    gl.glLightf(GL.GL_LIGHT0, GL.GL_LINEAR_ATTENUATION, 1.0f);
    gl.glLightf(GL.GL_LIGHT0, GL.GL_CONSTANT_ATTENUATION, 2.0f);

    gl.glLightfv(GL.GL_LIGHT1, GL.GL_POSITION, light_position1);
    gl.glLightfv(GL.GL_LIGHT1, GL.GL_DIFFUSE, white_light);
    gl.glLightfv(GL.GL_LIGHT1, GL.GL_SPECULAR, white_light);

    gl.glLightfv(GL.GL_LIGHT1, GL.GL_AMBIENT, light_ambient);
    gl.glLightfv(GL.GL_LIGHT1, GL.GL_DIFFUSE, light_diffuse);
    gl.glLightfv(GL.GL_LIGHT1, GL.GL_SPECULAR, light_specular);
    gl.glLightfv(GL.GL_LIGHT1, GL.GL_POSITION, light_position1);

    gl.glLightf(GL.GL_LIGHT1, GL.GL_LINEAR_ATTENUATION, 1.0f);
    gl.glLightf(GL.GL_LIGHT1, GL.GL_CONSTANT_ATTENUATION, 2.0f);

    gl.glLightModelfv(GL.GL_LIGHT_MODEL_AMBIENT, lmodel_ambient);

    gl.glEnable(GL.GL_LIGHTING);
    gl.glEnable(GL.GL_LIGHT0);
    gl.glEnable(GL.GL_LIGHT1);
    gl.glEnable(GL.GL_DEPTH_TEST);

    this.gl.glMatrixMode(GL.GL_PROJECTION);
    makeObject();

    this.gl.glShadeModel(GL.GL_FLAT);
  }

  public void resizeGL(int width, int height) {

    int side = width <= height ? width : height; // qMin(width, height);
    this.gl.glViewport((width - side) / 2, (height - side) / 2, side, side);
    // this.gl.glViewport(0, 0, width, height);

    this.gl.glMatrixMode(GL.GL_PROJECTION);
    this.gl.glLoadIdentity();
    // this.gl.glOrtho(-zoom, +zoom, +zoom, -zoom, -1000, 1000);
    this.gl.glFrustum(-zoom, +zoom, +zoom, -zoom, 1.5, 20.0);
    this.gl.glMatrixMode(GL.GL_MODELVIEW);
  }

  public void paintGL() {

    this.gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
    this.gl.glLoadIdentity();
    this.gl.glTranslated(0.0, 0.0, -10.0);
    this.gl.glRotated(xRot / 16.0, 1.0, 0.0, 0.0);
    this.gl.glRotated(yRot / 16.0, 0.0, 1.0, 0.0);
    this.gl.glRotated(zRot / 16.0, 0.0, 0.0, 1.0);

    if (isRemakeObject()) {
      makeObject();
      this.remakeObject = false;
      // setOkToDraw(false);
    }

    this.gl.glMatrixMode(GL.GL_MODELVIEW);
    this.gl.glCallList(this.gllist);
    this.gl.glFlush();

    this.gl.glMatrixMode(GL.GL_PROJECTION);
    this.gl.glLoadIdentity();
    // this.gl.glOrtho(-zoom, +zoom, +zoom, -zoom, -1000, 1000);
    this.gl.glFrustum(-zoom, +zoom, +zoom, -zoom, 1.5, 20.0);
    this.gl.glMatrixMode(GL.GL_MODELVIEW);

  }

  public void mousePressEvent(QMouseEvent event) {

    this.lastPos = event.pos();
  }

  public void mouseMoveEvent(QMouseEvent event) {

    int dx = event.x() - lastPos.x();
    int dy = event.y() - lastPos.y();

    if (event.buttons().value() == Qt.MouseButton.LeftButton.value()) {
      setXRotation(xRot + 8 * dy);
      setYRotation(yRot + 8 * dx);
    } else if (event.buttons().value() == Qt.MouseButton.RightButton.value()) {
      setXRotation(xRot + 8 * dy);
      setZRotation(zRot + 8 * dx);
    }

    // event.buttons() == Qt.MouseButton.

    lastPos = event.pos();
  }

  public void wheelEvent(QWheelEvent event) {

    if (event.delta() > 0) {
      final double zoom = this.zoom * ZOOM_FACTOR;
      // if (zoom < MAX_ZOOM)
      this.zoom = zoom;
    } else {
      final double zoom = this.zoom / ZOOM_FACTOR;
      // if (zoom > MIN_ZOOM)
      this.zoom = zoom;
    }

    System.out.println(event.delta() + " zoom=" + this.zoom);

    QRect geo = this.geometry();

    resize(geo.width(), geo.height());
    updateGL();
  }

  public void setXRotation(int angle) {

    angle = normalizeAngle(angle);
    if (angle != xRot) {
      xRot = angle;
      // emit xRotationChanged(angle);
      updateGL();
    }
  }

  public void setYRotation(int angle) {

    angle = normalizeAngle(angle);
    if (angle != yRot) {
      yRot = angle;
      // emit yRotationChanged(angle);
      updateGL();
    }
  }

  public void setZRotation(int angle) {

    angle = normalizeAngle(angle);
    if (angle != zRot) {
      zRot = angle;
      // emit zRotationChanged(angle);
      updateGL();
    }
  }

  private int normalizeAngle(int angle) {
    while (angle < 0)
      return angle += 360 * 16;
    while (angle > 360 * 16)
      return angle -= 360 * 16;

    return angle;
  }

  public void clear() {

    repaint();
  }

  private void makeObjectSphere(final float x, float y, float z, int xQuadrant,
      int yQuadrant, int zQuadrant) {

    if (this.gl == null)
      return;

    final GL gl = this.gl;

    DoubleBuffer eqnX =
        DoubleBuffer.wrap(new double[] {xQuadrant, 0.0, 0.0, 0.0}); // x <
    // 0

    DoubleBuffer eqnY =
        DoubleBuffer.wrap(new double[] {0.0, yQuadrant, 0.0, 0.0}); // y <
    // 0

    DoubleBuffer eqnZ =
        DoubleBuffer.wrap(new double[] {0.0, 0.0, zQuadrant, 0.0}); // y <
    // 0

    gl.glPushMatrix();

    gl.glTranslatef(x, y, z);

    gl.glClipPlane(GL.GL_CLIP_PLANE0, eqnX);
    gl.glEnable(GL.GL_CLIP_PLANE0);
    gl.glClipPlane(GL.GL_CLIP_PLANE1, eqnY);
    gl.glEnable(GL.GL_CLIP_PLANE1);
    gl.glClipPlane(GL.GL_CLIP_PLANE2, eqnZ);
    gl.glEnable(GL.GL_CLIP_PLANE2);

    this.glu.gluSphere(glu.gluNewQuadric(), LEN, STACKS, STACKS);
    // this.glut.glutSolidSphere(LEN, STACKS, STACKS);

    gl.glDisable(GL.GL_CLIP_PLANE0);
    gl.glDisable(GL.GL_CLIP_PLANE1);
    gl.glDisable(GL.GL_CLIP_PLANE2);

    gl.glPopMatrix();

    gl.glFlush();
  }

  private void makeObjectCylinder(final float x, float y, float z,
      int xQuadrant, int yQuadrant, int zQuadrant, final int sens) {

    if (this.gl == null)
      return;

    final GL gl = this.gl;

    DoubleBuffer eqnX =
        DoubleBuffer.wrap(new double[] {xQuadrant, 0.0, 0.0, 0.0}); // x <
    // 0

    DoubleBuffer eqnY =
        DoubleBuffer.wrap(new double[] {0.0, yQuadrant, 0.0, 0.0}); // y <
    // 0

    DoubleBuffer eqnZ =
        DoubleBuffer.wrap(new double[] {0.0, 0.0, zQuadrant, 0.0}); // y <
    // 0

    gl.glPushMatrix();

    gl.glTranslatef(x, y, z);

    if (sens == 1)
      gl.glRotatef(90, 0, 1, 0);
    else if (sens == 2)
      gl.glRotatef(90f, 1, 0, 0);

    gl.glClipPlane(GL.GL_CLIP_PLANE0, eqnX);
    gl.glEnable(GL.GL_CLIP_PLANE0);
    gl.glClipPlane(GL.GL_CLIP_PLANE1, eqnY);
    gl.glEnable(GL.GL_CLIP_PLANE1);
    gl.glClipPlane(GL.GL_CLIP_PLANE2, eqnZ);
    gl.glEnable(GL.GL_CLIP_PLANE2);

    final int stacks = sens == 0 ? STACKS : STACKS * 2;

    this.glu.gluCylinder(glu.gluNewQuadric(), LEN, LEN, LEN, stacks, stacks);
    // this.glut.glutSolidCylinder(LEN, LEN, stacks, stacks);

    gl.glDisable(GL.GL_CLIP_PLANE0);
    gl.glDisable(GL.GL_CLIP_PLANE1);
    gl.glDisable(GL.GL_CLIP_PLANE2);

    gl.glPopMatrix();

    gl.glFlush();
  }

  private static final float[][] boxNormals =
      { {-1.0f, 0.0f, 0.0f}, {0.0f, 1.0f, 0.0f}, {1.0f, 0.0f, 0.0f},
          {0.0f, -1.0f, 0.0f}, {0.0f, 0.0f, 1.0f}, {0.0f, 0.0f, -1.0f}};

  private void makeObjectSquare(final float x, float y, float z, final int sens) {

    if (this.gl == null)
      return;

    final GL gl = this.gl;

    gl.glPushMatrix();
    gl.glTranslatef(x, y, z);

    if (sens == 1)
      gl.glRotatef(90, 0, 1, 0);
    else if (sens == 2)
      gl.glRotatef(90, 1, 0, 0);

    gl.glNormal3fv(new float[] {0.0f, 0.0f, -1.0f}, 0);

    gl.glBegin(GL.GL_POLYGON);

    gl.glVertex3f(0, 0, 0);
    gl.glVertex3f(0, LEN, 0);
    gl.glVertex3f(LEN, LEN, 0);
    gl.glVertex3f(LEN, 0, 0);

    gl.glEnd();
    gl.glPopMatrix();

    gl.glFlush();
  }

  private void makeObject() {

    if (gllist > 0)
      this.gl.glDeleteLists(gllist, 1);
    int list = this.gl.glGenLists(++gllist);

    this.gl.glNewList(list, GL.GL_COMPILE);

    // makeObjectCylinder2(0, 0, 0, 1, 1, 1);
    // makeObjectCylinder2(-1,-1, -1, 1, 1, 1);
    // makeObjectCylinder2(1,1, 1, 1, 1, 1);

    // makeObjectSphere(0, 0.5f, 0);
    // makeObjectSquare(0, 0, 0);

    // First level

    makeObjectSphere(0, LEN, LEN, 1, 1, 1);

    makeObjectSquare(-LEN, 0, LEN * 2, 0);

    makeObjectCylinder(-LEN, LEN, LEN, -1, 1, 1, 1);
    makeObjectSphere(-LEN, LEN, LEN, -1, 1, 1);
    makeObjectCylinder(0, LEN, LEN, 1, 1, 1, 2);
    makeObjectCylinder(-LEN, LEN, LEN, -1, 1, 1, 2);
    makeObjectCylinder(-LEN, 0, LEN, -1, -1, 1, 1);
    makeObjectSphere(0, 0, LEN, 1, -1, 1);
    makeObjectSphere(-LEN, 0, LEN, -1, -1, 1);

    // Second level

    makeObjectSquare(-2 * LEN, 0, LEN, 1);
    makeObjectSquare(LEN, 0, LEN, 1);
    makeObjectSquare(-LEN, -LEN, 0, 2);
    makeObjectSquare(-LEN, 2 * LEN, 0, 2);

    makeObjectCylinder(0, LEN, 0, 1, 1, 1, 0);
    makeObjectCylinder(-LEN, LEN, 0, -1, 1, 1, 0);
    makeObjectCylinder(0, 0, 0, 1, -1, 1, 0);
    makeObjectCylinder(-LEN, 0, 0, -1, -1, 1, 0);

    // Third level

    makeObjectSphere(0, LEN, 0, 1, 1, -1);
    makeObjectCylinder(-LEN, LEN, 0, 1, 1, 1, 1);
    makeObjectSphere(-LEN, LEN, 0, -1, 1, -1);
    makeObjectCylinder(0, LEN, 0, 1, -1, 1, 2);
    makeObjectSphere(0, 0, 0, 1, -1, -1);

    makeObjectCylinder(-LEN, LEN, 0, -1, -1, 1, 2);

    makeObjectCylinder(-LEN, 0, 0, 1, -1, 1, 1);
    makeObjectSphere(-LEN, 0, 0, -1, -1, -1);

    makeObjectSquare(-LEN, 0, -LEN, 0);

    gl.glFlush();
    gl.glEndList();
    System.out.println(gllist);

  }

  private DoubleBuffer eqn =
      DoubleBuffer.wrap(new double[] {0.0, 1.0, 0.0, 0.0}); // y < 0

  private DoubleBuffer eqn3 =
      DoubleBuffer.wrap(new double[] {0.0, 0.0, 1.0, 1.0}); // x < 0
  private float spin = 0;

  private GLUquadric sphereQuadratic;// = glu.gluNewQuadric();

  private void SpinDisplay() {

    this.spin = spin + 2.0f;
    if (this.spin > 360.0)
      this.spin = spin - 360.0f;
  }

  public static void main(String[] args) {

    QApplication.initialize(args);
    TestGL testGL = new TestGL();
    testGL.show();
    QApplication.exec();

  }

}
