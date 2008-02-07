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

import com.trolltech.qt.gui.QImage;

/**
 * This utility class contains useful methods for Qt.
 * @author Laurent Jourdren
 */
public final class QtUtil {

  /**
   * Transform a color image into a gray image
   * @param srcImage source image
   * @return a gray image
   */
  public static final QImage toGrayscale(final QImage srcImage) {

    if (srcImage.isNull())
      return new QImage();

    QImage dstImage = new QImage(srcImage.size(), QImage.Format.Format_ARGB32);

    // Parsing pixels
    for (int y = 0; y < srcImage.height(); y++) {
      for (int x = 0; x < srcImage.width(); x++) {

        final int pixel = srcImage.pixel(x, y);

        final int alpha = (((pixel & 0xFF000000) >> 24) & 0xFF);
        final int red = (((pixel & 0x00FF0000) >> 16) & 0xFF);
        final int green = (((pixel & 0x0000FF00) >> 8) & 0xFF);
        final int blue = (pixel & 0xFF);

        final int gray = (red * 11 + green * 16 + blue * 5) / 32;

        final int newpixel = (alpha << 24) + (gray << 16) + (gray << 8) + gray;

        dstImage.setPixel(x, y, newpixel);
      }
    }

    return dstImage;
  }

  //
  // Constructor
  //

  /**
   * Private constructor.
   */
  private QtUtil() {
  }

}
