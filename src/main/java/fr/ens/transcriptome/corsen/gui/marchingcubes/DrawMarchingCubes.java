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

package fr.ens.transcriptome.corsen.gui.marchingcubes;

import java.util.Iterator;
import java.util.List;

import javax.media.opengl.GL;
import javax.vecmath.Point3f;

import fr.ens.transcriptome.corsen.model.Particle3D;

public class DrawMarchingCubes {

  private GL gl;

  public void showParticle3D(Particle3D particle) {

    final List<Point3f> triangles = MCCube.getTriangles(particle);

    this.gl.glBegin(GL.GL_TRIANGLES);

    for (Iterator<Point3f> iter = triangles.iterator(); iter.hasNext();) {

      final Point3f p1 = iter.next();
      final Point3f p2 = iter.next();
      final Point3f p3 = iter.next();

      final Point3f n = CalculateVectorNormal(p1, p2, p3);

      this.gl.glNormal3f(n.x, n.y, n.z);
      this.gl.glVertex3f(p1.x, p1.y, p1.z);
      this.gl.glNormal3f(n.x, n.y, n.z);
      this.gl.glVertex3f(p2.x, p2.y, p2.z);
      this.gl.glNormal3f(n.x, n.y, n.z);
      this.gl.glVertex3f(p3.x, p3.y, p3.z);
    }

    this.gl.glEnd();
  }

  private static final Point3f CalculateVectorNormal(Point3f fVert1,
      Point3f fVert2, Point3f fVert3) {

    final float Qx, Qy, Qz, Px, Py, Pz;

    Qx = fVert2.x - fVert1.x;
    Qy = fVert2.y - fVert1.y;
    Qz = fVert2.z - fVert1.z;
    Px = fVert3.x - fVert1.x;
    Py = fVert3.y - fVert1.y;
    Pz = fVert3.z - fVert1.z;

    final Point3f result = new Point3f();
    result.x = Py * Qz - Pz * Qy;
    result.y = Pz * Qx - Px * Qz;
    result.z = Px * Qy - Py * Qx;

    return result;
  }

  public DrawMarchingCubes(GL gl) {

    this.gl = gl;
  }

}
