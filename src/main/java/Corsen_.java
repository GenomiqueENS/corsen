import fr.ens.transcriptome.corsen.Globals;
import fr.ens.transcriptome.corsen.calc.Particles3D;
import fr.ens.transcriptome.corsen.model.Particle2D;
import fr.ens.transcriptome.corsen.model.Particle2DBuilder;
import fr.ens.transcriptome.corsen.model.Particle3D;
import fr.ens.transcriptome.corsen.model.Particle3DBuilder;
import fr.ens.transcriptome.corsen.util.CorsenImageJUtil;
import fr.ens.transcriptome.corsen.util.Util;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.LookUpTable;
import ij.Macro;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ByteStatistics;
import ij.process.ColorProcessor;
import ij.process.ColorStatistics;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.PolygonFiller;
import ij.process.ShortProcessor;
import ij.process.ShortStatistics;
import ij.text.TextWindow;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

/**
 * Implements ImageJ's Analyze Particles command.
 * <p>
 * 
 * <pre>
 *                                                                 for each line do
 *                                                                 for each pixel in this line do
 *                                                                 if the pixel value is &quot;inside&quot; the threshold range then
 *                                                                 trace the edge to mark the object
 *                                                                 do the measurement
 *                                                                 fill the object with a color outside the threshold range
 *                                                                 else
 *                                                                 continue the scan
 * </pre>
 */
public class Corsen_ implements PlugInFilter, Measurements {

  /** Display results in the ImageJ console. */
  public static final int SHOW_RESULTS = 1;

  /** Obsolete */
  public static final int SHOW_SUMMARY = 2;

  /** Display image containing outlines of measured paticles. */
  public static final int SHOW_OUTLINES = 4;

  /** Do not measure particles touching edge of image. */
  public static final int EXCLUDE_EDGE_PARTICLES = 8;

  /** Display a progress bar. */
  public static final int SHOW_PROGRESS = 32;

  /** Clear ImageJ console before starting. */
  public static final int CLEAR_WORKSHEET = 64;

  /**
   * Record starting coordinates so outline can be recreated later using
   * doWand(x,y).
   */
  public static final int RECORD_STARTS = 128;

  /** Display a summary. */
  public static final int DISPLAY_SUMMARY = 256;

  /** Do not display particle outline image. */
  public static final int SHOW_NONE = 512;

  /** Flood fill to ignore interior holes. */
  public static final int INCLUDE_HOLES = 1024;

  /** Change output file name. */
  public static final int CHANGE_OUTPUT_FILENAME = 2048;

  /** No confirm save dialog. */
  public static final int NO_CONFIRM_SAVE_DIALOG = 4096;

  /** Show Particles 3D. */
  public static final int SHOW_PARTICLES_3D = 8192;

  static final String OPTIONS = "ap.options";

  static final int BYTE = 0, SHORT = 1, FLOAT = 2, RGB = 3;
  static final double DEFAULT_MIN_SIZE = 0.0;
  static final double DEFAULT_MAX_SIZE = Double.POSITIVE_INFINITY;

  private static double staticMinSize = 0.0;
  private static double staticMaxSize = DEFAULT_MAX_SIZE;
  private static int staticOptions = Prefs.getInt(OPTIONS, CLEAR_WORKSHEET);
  private static String[] showStrings = {"Nothing", "Outlines", "Masks",
      "Ellipses"};
  private static double minCircularity = 0.0, maxCircularity = 1.0;

  protected static final int NOTHING = 0, OUTLINES = 1, MASKS = 2,
      ELLIPSES = 3;
  protected static int showChoice;
  protected ImagePlus imp;
  protected ResultsTable rt;
  protected Analyzer analyzer;
  protected int slice;
  protected boolean processStack;
  protected boolean showResults, excludeEdgeParticles, showSizeDistribution,
      resetCounter, showProgress, recordStarts, displaySummary, floodFill;

  private double level1, level2;
  private double minSize;
  private double maxSize;
  private int options;
  private int measurements;
  private Calibration calibration;
  private String arg;
  private double fillColor;
  private boolean thresholdingLUT;
  private ImageProcessor drawIP;
  private int width, height;
  private boolean canceled;
  private ImageStack outlines;
  private IndexColorModel customLut;
  private int particleCount;
  private int totalCount;
  private TextWindow tw;
  private Wand wand;
  private int imageType, imageType2;
  private int xStartC, yStartC;
  private boolean roiNeedsImage;
  private int minX, maxX, minY, maxY;
  private ImagePlus redirectImp;
  private ImageProcessor redirectIP;
  private PolygonFiller pf;
  private Roi saveRoi;
  private int beginningCount;
  private Rectangle r;
  private ImageProcessor mask;
  private double totalArea;
  private FloodFiller ff;
  private Polygon polygon;

  // Add by Laurent Jourdren
  private Set<Particle3DBuilder> particles3D = new HashSet<Particle3DBuilder>();
  private Set<Particle2D> previousParticules2D = new HashSet<Particle2D>();
  private Set<Particle2D> currentParticles2D = new HashSet<Particle2D>();
  private Map<Particle2D, Particle3DBuilder> previousParticles3D = new HashMap<Particle2D, Particle3DBuilder>();
  private Map<Particle2D, Particle3DBuilder> currentParticles3D = new HashMap<Particle2D, Particle3DBuilder>();
  private int previousZ = -1;
  private static final boolean DEBUG = false;

  private static final String EXTENSION_FILE = ".par";

  /*
   * Add by Laurent Jourdren
   */
  void savePolygonXY(ImagePlus imp, Roi roi) {

    // double zHeight= 2.0;

    Calibration cal = imp.getCalibration();
    final double pixelWidth = cal.pixelWidth;
    final double pixelHeight = cal.pixelHeight;
    final double pixelDepth = cal.pixelDepth;

    final int slice = imp.getCurrentSlice();

    if (slice - 1 != this.previousZ) {
      this.previousParticules2D = this.currentParticles2D;
      this.currentParticles2D = new HashSet<Particle2D>();
      this.previousParticles3D = this.currentParticles3D;
      this.currentParticles3D = new HashMap<Particle2D, Particle3DBuilder>();

      if (this.previousZ == -1)
        this.previousZ = slice - 1;
      else
        this.previousZ++;
    }

    Particle2D p2D = Particle2DBuilder.createParticle2D((float) pixelWidth,
        (float) pixelHeight, imp, (PolygonRoi) roi);

    Iterator it = this.previousParticules2D.iterator();

    boolean find = false;

    while (it.hasNext()) {

      final Particle2D p2DToTest = (Particle2D) it.next();

      if (p2DToTest.innerPointIntersect(p2D)) {

        Particle3DBuilder existingP3D = this.previousParticles3D.get(p2DToTest);

        existingP3D.add(p2D, slice);

        if (DEBUG)
          System.out.println("Add p2D to " + existingP3D.getId() + " z="
              + slice + " (" + p2D.innerPointsCount() + " points)");

        if (this.currentParticles3D.containsKey(p2D)) {
          Particle3DBuilder particle2 = this.currentParticles3D.get(p2D);

          if (particle2.getId() != existingP3D.getId()) {
            particle2.add(existingP3D.getParticle());
            this.particles3D.remove(existingP3D);

          }

          if (DEBUG)
            System.out.println("Merge " + existingP3D.getId() + " in "
                + particle2.getId() + " z=" + slice);

        } else
          this.currentParticles3D.put(p2D, existingP3D);

        find = true;
      }

    }

    if (!find) {

      Particle3DBuilder newP3D = new Particle3DBuilder((float) pixelWidth,
          (float) pixelHeight, (float) pixelDepth);
      newP3D.setName(imp.getTitle() + "-" + newP3D.getId());
      newP3D.add(p2D, slice);

      this.particles3D.add(newP3D);
      this.currentParticles3D.put(p2D, newP3D);

      if (DEBUG)
        System.out.println("New 3D Object (" + newP3D.getId() + "), z=" + slice
            + " add particle2D #" + p2D.getId() + " (" + p2D.innerPointsCount()
            + " points)");

    }

    this.currentParticles2D.add(p2D);
  }

  private int countInnerParticlesPoints() {

    if (this.particles3D != null) {

      Iterator it = this.particles3D.iterator();
      int count = 0;

      while (it.hasNext()) {

        Particle3D p = (Particle3D) it.next();
        count += p.innerPointsCount();
      }

      return count;
    }

    return 0;
  }

  private void saveParticles3D(Writer writer, FileInfo fi) throws IOException {

    Iterator it = this.particles3D.iterator();

    writer.write("# Generated by ");
    writer.write(Globals.APP_NAME);
    writer.write(" version ");
    writer.write(Globals.APP_VERSION);
    writer.write(" (");
    writer.write(Globals.APP_BUILD_NUMBER);
    writer.write(", ");
    writer.write(Globals.APP_BUILD_DATE);
    writer.write(")");
    writer.write("\n# Generated on ");
    writer.write(new Date(System.currentTimeMillis()).toString());

    File f = new File(fi.directory, fi.fileName);

    writer.write("\n# Image file name:");
    writer.write(f.getAbsolutePath());
    writer.write("\n# Image created on ");

    writer.write(new Date(f.lastModified()).toString());

    ImageProcessor ip = this.imp.getProcessor();
    Calibration cal = imp.getCalibration();

    writer.write("\nParFileVersion=1.1");

    writer.write("\n" + Particles3D.WIDTH_KEY + "=" + this.imp.getWidth());
    writer.write("\n" + Particles3D.HEIGHT_KEY + "=" + this.imp.getHeight());
    writer.write("\n" + Particles3D.ZSLICES_KEY + "=" + this.imp.getNSlices());

    writer.write("\n" + Particles3D.PIXEL_WIDTH_KEY + "=" + cal.pixelWidth);
    writer.write("\n" + Particles3D.PIXEL_HEIGHT_KEY + "=" + cal.pixelHeight);
    writer.write("\n" + Particles3D.PIXEL_DEPTH_KEY + "=" + cal.pixelDepth);
    writer.write("\n" + Particles3D.UNIT_OF_LENGHT_KEY + "=" + cal.getUnit());

    writer.write("\n" + Particles3D.MIN_THRESHOLD_KEY + "="
        + ip.getMinThreshold());
    writer.write("\n" + Particles3D.MAX_THRESHOLD_KEY + "="
        + ip.getMaxThreshold());

    /*
     * writer.write("\n# Min threshold: " + ip.getMinThreshold());
     * writer.write("\n# Max threshold: " + ip.getMaxThreshold());
     * writer.write("\nDimensions"); writer.write("\t" + (this.imp.getWidth() *
     * imp.pixelWidth)); writer.write("\t" + (this.imp.getHeight() *
     * imp.pixelHeight)); writer.write("\t" + (this.imp.getNSlices() *
     * imp.pixelDepth));
     */

    writer
        .write("\nName\tCenter\tBarycenter\tVolume\tIntensity\tSurface points\tInner points\n");

    while (it.hasNext()) {

      Particle3D p = ((Particle3DBuilder) it.next()).getParticle();

      if (p.getIntensity() == 0)
        continue;

      writer.write(p.toString());
      writer.write('\n');
    }

  }

  private void showParticles3D(final ImagePlus imp) {

    if ((options & SHOW_PARTICLES_3D) != 0) {

      Iterator it = this.particles3D.iterator();

      int i = 1;
      int n = this.particles3D.size();

      while (it.hasNext()) {

        Particle3D p = (Particle3D) it.next();

        ImagePlus img = CorsenImageJUtil.createImageParticles3D(imp.getWidth(),
            imp.getHeight(), p);

        if (DEBUG) {
          final int nbStack = img.getStack().getSize();
          System.out.println("Particle " + i + "/" + n + " nbStacks: "
              + nbStack + " Surface: " + p.surfacePointsCount() + " Inner: "
              + p.innerPointsCount());
        }

        if (p.innerPointsCount() > 100)
          img.show();

        i++;
      }
    }

  }

  private void saveParticle(ImagePlus imp) throws IOException {

    File file = null;

    if ((options & CHANGE_OUTPUT_FILENAME) != 0) {
      JFileChooser chooser = new JFileChooser();

      final FileFilter ff = new FileFilter() {

        public boolean accept(File f) {
          if (f.isDirectory()) {
            return true;
          }

          String extension = Util.getExtension(f);

          if (extension != null) {
            if (extension.equals(Globals.EXTENSION_PARTICLES_FILE))
              return true;

          }

          return false;
        }

        // The description of this filter
        public String getDescription() {
          return "Particle file (*.par)";
        }
      };

      chooser.setFileFilter(ff);

      int result = chooser.showSaveDialog(imp.getWindow());

      if (result == JFileChooser.APPROVE_OPTION) {
        file = chooser.getSelectedFile();
      } else
        return;

    } else {

      boolean writeFile = false;

      if ((options & NO_CONFIRM_SAVE_DIALOG) == 0) {

        final int response = JOptionPane.showConfirmDialog(imp.getWindow(),
            new String[] {"Save results ?"}, "Save results ?",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {

          writeFile = true;
        } else
          return;

      } else
        writeFile = true;

      if (writeFile) {
        FileInfo fi = imp.getOriginalFileInfo();

        String newName;

        int index = fi.fileName.indexOf(".");
        if (index == -1)
          newName = fi.fileName;
        else
          newName = fi.fileName.substring(0, index);

        file = new File(fi.directory, newName + EXTENSION_FILE);
      }

    }

    if (file != null) {

      FileOutputStream fos = new FileOutputStream(file);
      Writer writer = new OutputStreamWriter(fos);
      saveParticles3D(writer, imp.getOriginalFileInfo());
      writer.close();
    }

    /*
     * final Particles3D mitosParticles = new Particles3D(file); RGL rgl = new
     * RGL(file.getParentFile(), file.getName() + ".R");
     * rgl.writeRPlots(mitosParticles, "red", false); rgl.close();
     */

  }

  /**
   * Construct a ParticleAnalyzer.
   * @param options a flag word created by Oring SHOW_RESULTS,
   *          EXCLUDE_EDGE_PARTICLES, etc.
   * @param measurements a flag word created by ORing constants defined in the
   *          Measurements interface
   * @param rt a ResultsTable where the measurements will be stored
   * @param minSize the smallest particle size in pixels
   * @param maxSize the largest particle size in pixels
   */
  public Corsen_(int options, int measurements, ResultsTable rt,
      double minSize, double maxSize) {
    this.options = options;
    this.measurements = measurements;
    this.rt = rt;
    if (this.rt == null)
      this.rt = new ResultsTable();
    this.minSize = minSize;
    this.maxSize = maxSize;
    slice = 1;
  }

  /** Default constructor */
  public Corsen_() {
    slice = 1;
  }

  public int setup(String arg, ImagePlus imp) {
    this.arg = arg;
    this.imp = imp;

    IJ.register(Corsen_.class);
    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    if (!showDialog())
      return DONE;
    int baseFlags = DOES_8G + DOES_16 + DOES_32 + NO_CHANGES + NO_UNDO;
    int flags = IJ.setupDialog(imp, baseFlags);
    processStack = (flags & DOES_STACKS) != 0;
    slice = 0;
    saveRoi = imp.getRoi();
    if (saveRoi != null && saveRoi.getType() != Roi.RECTANGLE
        && saveRoi.isArea())
      polygon = saveRoi.getPolygon();
    imp.startTiming();
    return flags;
  }

  public void run(ImageProcessor ip) {
    if (canceled)
      return;
    slice++;
    if (imp.getStackSize() > 1 && processStack)
      imp.setSlice(slice);
    if (!analyze(imp, ip))
      canceled = true;
    if (slice == imp.getStackSize()) {
      imp.updateAndDraw();
      if (saveRoi != null)
        imp.setRoi(saveRoi);
    }

    if (slice == imp.getNSlices()) {
      try {
        showParticles3D(imp);
        saveParticle(imp);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  /** Displays a modal options dialog. */
  public boolean showDialog() {
    Calibration cal = imp.getCalibration();
    double unitSquared = cal.pixelWidth * cal.pixelHeight;
    if (Macro.getOptions() != null) {
      boolean oldMacro = updateMacroOptions();
      if (oldMacro)
        unitSquared = 1.0;
    }
    GenericDialog gd = new GenericDialog(Globals.getWindowsTitle());
    minSize = staticMinSize;
    maxSize = staticMaxSize;
    if (maxSize == 999999)
      maxSize = DEFAULT_MAX_SIZE;
    options = staticOptions;
    String units = cal.getUnit() + "^2";
    int places = 0;
    double cmin = minSize * unitSquared;
    if ((int) cmin != cmin)
      places = 2;
    double cmax = maxSize * unitSquared;
    if ((int) cmax != cmax && cmax != DEFAULT_MAX_SIZE)
      places = 2;
    String minStr = IJ.d2s(cmin, places);
    if (minStr.indexOf("-") != -1) {
      for (int i = places; i <= 6; i++) {
        minStr = IJ.d2s(cmin, i);
        if (minStr.indexOf("-") == -1)
          break;
      }
    }
    String maxStr = IJ.d2s(cmax, places);
    if (maxStr.indexOf("-") != -1) {
      for (int i = places; i <= 6; i++) {
        maxStr = IJ.d2s(cmax, i);
        if (maxStr.indexOf("-") == -1)
          break;
      }
    }
    gd.addStringField("Size (" + units + "):", minStr + "-" + maxStr, 12);
    gd.addStringField("Circularity:", IJ.d2s(minCircularity) + "-"
        + IJ.d2s(maxCircularity), 12);
    gd.addChoice("Show:", showStrings, showStrings[showChoice]);

    String[] labels = new String[9];
    boolean[] states = new boolean[9];
    labels[0] = "Display Results";
    states[0] = (options & SHOW_RESULTS) != 0;
    labels[1] = "Exclude on Edges";
    states[1] = (options & EXCLUDE_EDGE_PARTICLES) != 0;
    labels[2] = "Clear Results";
    states[2] = (options & CLEAR_WORKSHEET) != 0;
    labels[3] = "Include Holes";
    states[3] = (options & INCLUDE_HOLES) != 0;
    labels[4] = "Summarize";
    states[4] = (options & DISPLAY_SUMMARY) != 0;
    labels[5] = "Record Starts";
    states[5] = (options & RECORD_STARTS) != 0;
    labels[6] = "Change output file name";
    states[6] = (options & CHANGE_OUTPUT_FILENAME) != 0;
    labels[7] = "No confirm save dialog";
    states[7] = (options & NO_CONFIRM_SAVE_DIALOG) != 0;
    labels[8] = "Show Particles 3D";
    states[8] = (options & SHOW_PARTICLES_3D) != 0;

    gd.addCheckboxGroup(5, 2, labels, states);

    gd.showDialog();
    if (gd.wasCanceled())
      return false;

    String[] minAndMax = Tools.split(gd.getNextString(), " -");
    double mins = Tools.parseDouble(minAndMax[0]);
    double maxs = minAndMax.length == 2 ? Tools.parseDouble(minAndMax[1])
        : Double.NaN;
    minSize = Double.isNaN(mins) ? DEFAULT_MIN_SIZE : mins / unitSquared;
    maxSize = Double.isNaN(maxs) ? DEFAULT_MAX_SIZE : maxs / unitSquared;
    if (minSize < DEFAULT_MIN_SIZE)
      minSize = DEFAULT_MIN_SIZE;
    if (maxSize < minSize)
      maxSize = DEFAULT_MAX_SIZE;

    minAndMax = Tools.split(gd.getNextString(), " -");
    double minc = Tools.parseDouble(minAndMax[0]);
    double maxc = minAndMax.length == 2 ? Tools.parseDouble(minAndMax[1])
        : Double.NaN;
    minCircularity = Double.isNaN(minc) ? 0.0 : minc;
    maxCircularity = Double.isNaN(maxc) ? 1.0 : maxc;
    if (minCircularity < 0.0 || minCircularity > 1.0)
      minCircularity = 0.0;
    if (maxCircularity < minCircularity || maxCircularity > 1.0)
      maxCircularity = 1.0;
    if (minCircularity == 1.0 && maxCircularity == 1.0)
      minCircularity = 0.0;

    if (gd.invalidNumber()) {
      IJ.error("Bins invalid.");
      canceled = true;
      return false;
    }
    staticMinSize = minSize;
    staticMaxSize = maxSize;
    showChoice = gd.getNextChoiceIndex();

    if (gd.getNextBoolean())
      options |= SHOW_RESULTS;
    else
      options &= ~SHOW_RESULTS;

    if (gd.getNextBoolean())
      options |= EXCLUDE_EDGE_PARTICLES;
    else
      options &= ~EXCLUDE_EDGE_PARTICLES;

    if (gd.getNextBoolean())
      options |= CLEAR_WORKSHEET;
    else
      options &= ~CLEAR_WORKSHEET;

    if (gd.getNextBoolean())
      options |= INCLUDE_HOLES;
    else
      options &= ~INCLUDE_HOLES;

    if (gd.getNextBoolean())
      options |= DISPLAY_SUMMARY;
    else
      options &= ~DISPLAY_SUMMARY;

    if (gd.getNextBoolean())
      options |= RECORD_STARTS;
    else
      options &= ~RECORD_STARTS;

    if (gd.getNextBoolean())
      options |= CHANGE_OUTPUT_FILENAME;
    else
      options &= ~CHANGE_OUTPUT_FILENAME;

    if (gd.getNextBoolean())
      options |= NO_CONFIRM_SAVE_DIALOG;
    else
      options &= ~NO_CONFIRM_SAVE_DIALOG;

    if (gd.getNextBoolean())
      options |= SHOW_PARTICLES_3D;
    else
      options &= ~SHOW_PARTICLES_3D;

    staticOptions = options;
    options |= SHOW_PROGRESS;
    if ((options & DISPLAY_SUMMARY) != 0)
      Analyzer.setMeasurements(Analyzer.getMeasurements() | AREA);
    return true;
  }

  boolean updateMacroOptions() {
    String options = Macro.getOptions();
    int index = options.indexOf("maximum=");
    if (index == -1)
      return false;
    index += 8;
    int len = options.length();
    while (index < len - 1 && options.charAt(index) != ' ')
      index++;
    if (index == len - 1)
      return false;
    int min = (int) Tools.parseDouble(Macro.getValue(options, "minimum", "1"));
    int max = (int) Tools.parseDouble(Macro.getValue(options, "maximum",
        "999999"));
    options = "size=" + min + "-" + max + options.substring(index, len);
    Macro.setOptions(options);
    return true;
  }

  /**
   * Performs particle analysis on the specified image. Returns false if there
   * is an error.
   */
  public boolean analyze(ImagePlus imp) {
    return analyze(imp, imp.getProcessor());
  }

  /**
   * Performs particle analysis on the specified ImagePlus and ImageProcessor.
   * Returns false if there is an error.
   */
  public boolean analyze(ImagePlus imp, ImageProcessor ip) {
    showResults = (options & SHOW_RESULTS) != 0;
    excludeEdgeParticles = (options & EXCLUDE_EDGE_PARTICLES) != 0;
    resetCounter = (options & CLEAR_WORKSHEET) != 0;
    showProgress = (options & SHOW_PROGRESS) != 0;
    floodFill = (options & INCLUDE_HOLES) == 0;
    recordStarts = (options & RECORD_STARTS) != 0;
    displaySummary = (options & DISPLAY_SUMMARY) != 0;
    if ((options & SHOW_OUTLINES) != 0)
      showChoice = OUTLINES;
    if ((options & SHOW_NONE) != 0)
      showChoice = NOTHING;
    ip.snapshot();
    ip.setProgressBar(null);
    if (Analyzer.isRedirectImage()) {
      redirectImp = Analyzer.getRedirectImage(imp);
      if (redirectImp == null)
        return false;
      int depth = redirectImp.getStackSize();
      if (depth > 1 && depth == imp.getStackSize()) {
        ImageStack redirectStack = redirectImp.getStack();
        redirectIP = redirectStack.getProcessor(imp.getCurrentSlice());
      } else
        redirectIP = redirectImp.getProcessor();
    }
    if (!setThresholdLevels(imp, ip))
      return false;
    width = ip.getWidth();
    height = ip.getHeight();
    if (showChoice != NOTHING) {
      if (slice == 1)
        outlines = new ImageStack(width, height);
      drawIP = new ByteProcessor(width, height);
      if (showChoice == MASKS)
        drawIP.invertLut();
      else if (showChoice == OUTLINES) {
        if (customLut == null)
          makeCustomLut();
        drawIP.setColorModel(customLut);
        drawIP.setFont(new Font("SansSerif", Font.PLAIN, 9));

      }
      outlines.addSlice(null, drawIP);
      drawIP.setColor(Color.white);
      drawIP.fill();
      drawIP.setColor(Color.black);
    }
    calibration = redirectImp != null ? redirectImp.getCalibration() : imp
        .getCalibration();

    if (rt == null) {
      rt = Analyzer.getResultsTable();
      analyzer = new Analyzer(imp);
    } else
      analyzer = new Analyzer(imp, measurements, rt);
    if (resetCounter && slice == 1) {
      if (!Analyzer.resetCounter())
        return false;
    }
    beginningCount = Analyzer.getCounter();

    byte[] pixels = null;
    if (ip instanceof ByteProcessor)
      pixels = (byte[]) ip.getPixels();
    if (r == null) {
      r = ip.getRoi();
      mask = ip.getMask();
      if (displaySummary) {
        if (mask != null)
          totalArea = ImageStatistics.getStatistics(ip, AREA, calibration).area;
        else
          totalArea = r.width * calibration.pixelWidth * r.height
              * calibration.pixelHeight;
      }
    }
    minX = r.x;
    maxX = r.x + r.width;
    minY = r.y;
    maxY = r.y + r.height;
    if (r.width < width || r.height < height || mask != null) {
      if (!eraseOutsideRoi(ip, r, mask))
        return false;
    }
    int offset;
    double value;
    int inc = Math.max(r.height / 25, 1);
    int mi = 0;
    if (recordStarts) {
      xStartC = getColumnID("XStart");
      yStartC = getColumnID("YStart");
    }
    ImageWindow win = imp.getWindow();
    if (win != null)
      win.running = true;
    if (measurements == 0)
      measurements = Analyzer.getMeasurements();
    if (showChoice == ELLIPSES)
      measurements |= ELLIPSE;
    measurements &= ~LIMIT; // ignore "Limit to Threshold"
    roiNeedsImage = (measurements & PERIMETER) != 0
        || (measurements & CIRCULARITY) != 0 || (measurements & FERET) != 0;
    particleCount = 0;
    wand = new Wand(ip);
    pf = new PolygonFiller();
    if (floodFill) {
      ImageProcessor ipf = ip.duplicate();
      ipf.setValue(fillColor);
      ff = new FloodFiller(ipf);
    }

    for (int y = r.y; y < (r.y + r.height); y++) {
      offset = y * width;
      for (int x = r.x; x < (r.x + r.width); x++) {
        if (pixels != null)
          value = pixels[offset + x] & 255;
        else if (imageType == SHORT)
          value = ip.getPixel(x, y);
        else
          value = ip.getPixelValue(x, y);
        if (value >= level1 && value <= level2)
          analyzeParticle(x, y, imp, ip);
      }
      if (showProgress && ((y % inc) == 0))
        IJ.showProgress((double) (y - r.y) / r.height);
      if (win != null)
        canceled = !win.running;
      if (canceled) {
        Macro.abort();
        break;
      }
    }
    if (showProgress)
      IJ.showProgress(1.0);
    imp.killRoi();
    ip.resetRoi();
    ip.reset();
    if (displaySummary && processStack && IJ.getInstance() != null)
      updateSliceSummary();
    totalCount += particleCount;
    if (!canceled)
      showResults();
    return true;
  }

  void updateSliceSummary() {
    float[] areas = rt.getColumn(ResultsTable.AREA);
    String label = imp.getStack().getShortSliceLabel(slice);
    label = label != null && !label.equals("") ? label : "" + slice;
    String aLine;
    if (areas != null) {
      double sum = 0.0;
      int start = areas.length - particleCount;
      if (start < 0)
        return;
      for (int i = start; i < areas.length; i++)
        sum += areas[i];
      int places = Analyzer.getPrecision();
      Calibration cal = imp.getCalibration();
      String total = "\t" + IJ.d2s(sum, places);
      String average = "\t" + IJ.d2s(sum / particleCount, places);
      String fraction = "\t" + IJ.d2s(sum * 100.0 / totalArea, 1);
      aLine = label + "\t" + particleCount + total + average + fraction;
    } else
      aLine = label + "\t" + particleCount;
    if (tw == null) {
      String title = "Summary of " + imp.getTitle();
      String headings = "Slice\tCount\tTotal Area\tAverage Size\tArea Fraction";
      tw = new TextWindow(title, headings, aLine, 180, 360);
    } else
      tw.append(aLine);
  }

  boolean eraseOutsideRoi(ImageProcessor ip, Rectangle r, ImageProcessor mask) {
    int width = ip.getWidth();
    int height = ip.getHeight();
    ip.setRoi(r);
    if (excludeEdgeParticles && polygon != null) {
      ImageStatistics stats = ImageStatistics.getStatistics(ip, MIN_MAX, null);
      if (fillColor >= stats.min && fillColor <= stats.max) {
        double replaceColor = level1 - 1.0;
        if (replaceColor < 0.0 || replaceColor == fillColor) {
          replaceColor = level2 + 1.0;
          int maxColor = imageType == BYTE ? 255 : 65535;
          if (replaceColor > maxColor || replaceColor == fillColor) {
            IJ.error("Particle Analyzer", "Unable to remove edge particles");
            return false;
          }
        }
        for (int y = minY; y < maxY; y++) {
          for (int x = minX; x < maxX; x++) {
            int v = ip.getPixel(x, y);
            if (v == fillColor)
              ip.putPixel(x, y, (int) replaceColor);
          }
        }
      }
    }
    ip.setValue(fillColor);
    if (mask != null) {
      mask = mask.duplicate();
      mask.invert();
      ip.fill(mask);
    }
    ip.setRoi(0, 0, r.x, height);
    ip.fill();
    ip.setRoi(r.x, 0, r.width, r.y);
    ip.fill();
    ip.setRoi(r.x, r.y + r.height, r.width, height - (r.y + r.height));
    ip.fill();
    ip.setRoi(r.x + r.width, 0, width - (r.x + r.width), height);
    ip.fill();
    ip.resetRoi();
    // IJ.log("erase: "+fillColor+" "+level1+" "+level2+"
    // "+excludeEdgeParticles);
    // (new ImagePlus("ip2", ip.duplicate())).show();
    return true;
  }

  boolean setThresholdLevels(ImagePlus imp, ImageProcessor ip) {
    double t1 = ip.getMinThreshold();
    double t2 = ip.getMaxThreshold();
    boolean invertedLut = imp.isInvertedLut();
    boolean byteImage = ip instanceof ByteProcessor;
    if (ip instanceof ShortProcessor)
      imageType = SHORT;
    else if (ip instanceof FloatProcessor)
      imageType = FLOAT;
    else
      imageType = BYTE;
    if (t1 == ip.NO_THRESHOLD) {
      ImageStatistics stats = imp.getStatistics();
      if (imageType != BYTE
          || (stats.histogram[0] + stats.histogram[255] != stats.pixelCount)) {
        IJ.error("Particle Analyzer",
            "A thresholded image or 8-bit binary image is\n"
                + "required. Threshold levels can be set using\n"
                + "the Image->Adjust->Threshold tool.");
        canceled = true;
        return false;
      }
      if (invertedLut) {
        level1 = 255;
        level2 = 255;
        fillColor = 64;
      } else {
        level1 = 0;
        level2 = 0;
        fillColor = 192;
      }
    } else {
      level1 = t1;
      level2 = t2;
      if (imageType == BYTE) {
        if (level1 > 0)
          fillColor = 0;
        else if (level2 < 255)
          fillColor = 255;
      } else if (imageType == SHORT) {
        if (level1 > 0)
          fillColor = 0;
        else if (level2 < 65535)
          fillColor = 65535;
      } else if (imageType == FLOAT)
        fillColor = -Float.MAX_VALUE;
      else
        return false;
    }
    imageType2 = imageType;
    if (redirectIP != null) {
      if (redirectIP instanceof ShortProcessor)
        imageType2 = SHORT;
      else if (redirectIP instanceof FloatProcessor)
        imageType2 = FLOAT;
      else if (redirectIP instanceof ColorProcessor)
        imageType2 = RGB;
      else
        imageType2 = BYTE;
    }
    return true;
  }

  int counter = 0;

  void analyzeParticle(int x, int y, ImagePlus imp, ImageProcessor ip) {
    // Wand wand = new Wand(ip);
    ImageProcessor ip2 = redirectIP != null ? redirectIP : ip;
    wand.autoOutline(x, y, level1, level2);
    if (wand.npoints == 0) {
      IJ.log("wand error: " + x + " " + y);
      return;
    }
    Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints,
        Roi.TRACED_ROI);
    Rectangle r = roi.getBounds();
    if (r.width > 1 && r.height > 1) {
      PolygonRoi proi = (PolygonRoi) roi;
      pf.setPolygon(proi.getXCoordinates(), proi.getYCoordinates(), proi
          .getNCoordinates());
      ip2.setMask(pf.getMask(r.width, r.height));
      if (floodFill)
        ff.particleAnalyzerFill(x, y, level1, level2, ip2.getMask(), r);
    }
    ip2.setRoi(r);
    ip.setValue(fillColor);
    ImageStatistics stats = getStatistics(ip2, measurements, calibration);
    boolean include = true;
    if (excludeEdgeParticles) {
      if (r.x == minX || r.y == minY || r.x + r.width == maxX
          || r.y + r.height == maxY)
        include = false;
      if (polygon != null) {
        Rectangle bounds = roi.getBounds();
        int x1 = bounds.x + wand.xpoints[wand.npoints - 1];
        int y1 = bounds.y + wand.ypoints[wand.npoints - 1];
        int x2, y2;
        for (int i = 0; i < wand.npoints; i++) {
          x2 = bounds.x + wand.xpoints[i];
          y2 = bounds.y + wand.ypoints[i];
          if (!polygon.contains(x2, y2)) {
            include = false;
            break;
          }
          if ((x1 == x2 && ip.getPixel(x1, y1 - 1) == fillColor)
              || (y1 == y2 && ip.getPixel(x1 - 1, y1) == fillColor)) {
            include = false;
            break;
          }
          x1 = x2;
          y1 = y2;
        }
      }
    }
    ImageProcessor mask = ip2.getMask();
    if (minCircularity > 0.0 || maxCircularity < 1.0) {
      double perimeter = roi.getLength();
      double circularity = perimeter == 0.0 ? 0.0 : 4.0 * Math.PI
          * (stats.pixelCount / (perimeter * perimeter));
      if (circularity > 1.0)
        circularity = 0.0;
      // IJ.log(circularity+" "+perimeter+" "+stats.area);
      if (circularity < minCircularity || circularity > maxCircularity)
        include = false;
    }
    if (stats.pixelCount >= minSize && stats.pixelCount <= maxSize && include) {
      particleCount++;
      if (roiNeedsImage)
        roi.setImage(imp);
      saveResults(stats, roi);

      // Add by Laurent Jourdren
      savePolygonXY(imp, roi);

      if (showChoice != NOTHING)
        drawParticle(drawIP, roi, stats, mask);
    }
    if (redirectIP != null)
      ip.setRoi(r);
    ip.fill(mask);
  }

  ImageStatistics getStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
    switch (imageType2) {
    case BYTE:
      return new ByteStatistics(ip, mOptions, cal);
    case SHORT:
      return new ShortStatistics(ip, mOptions, cal);
    case FLOAT:
      return new FloatStatistics(ip, mOptions, cal);
    case RGB:
      return new ColorStatistics(ip, mOptions, cal);
    default:
      return null;
    }
  }

  /**
   * Saves statistics for one particle in a results table. This is a method
   * subclasses may want to override.
   */
  protected void saveResults(ImageStatistics stats, Roi roi) {
    analyzer.saveResults(stats, roi);
    if (recordStarts) {
      int coordinates = ((PolygonRoi) roi).getNCoordinates();
      Rectangle r = roi.getBounds();
      int x = r.x + ((PolygonRoi) roi).getXCoordinates()[coordinates - 1];
      int y = r.y + ((PolygonRoi) roi).getYCoordinates()[coordinates - 1];
      rt.addValue(xStartC, x);
      rt.addValue(yStartC, y);
    }
    if (showResults)
      analyzer.displayResults();
  }

  /**
   * Draws a selected particle in a separate image. This is another method
   * subclasses may want to override.
   */
  protected void drawParticle(ImageProcessor drawIP, Roi roi,
      ImageStatistics stats, ImageProcessor mask) {
    switch (showChoice) {
    case MASKS:
      drawFilledParticle(drawIP, roi, mask);
      break;
    case OUTLINES:
      drawOutline(drawIP, roi, rt.getCounter());
      break;
    case ELLIPSES:
      drawEllipse(drawIP, stats, rt.getCounter());
      break;
    default:
    }
  }

  void drawFilledParticle(ImageProcessor ip, Roi roi, ImageProcessor mask) {
    // IJ.write(roi.getBounds()+" "+mask.length);
    ip.setRoi(roi.getBounds());
    ip.fill(mask);
  }

  void drawOutline(ImageProcessor ip, Roi roi, int count) {
    Rectangle r = roi.getBounds();
    int nPoints = ((PolygonRoi) roi).getNCoordinates();
    int[] xp = ((PolygonRoi) roi).getXCoordinates();
    int[] yp = ((PolygonRoi) roi).getYCoordinates();
    int x = r.x, y = r.y;
    ip.setValue(0.0);
    ip.moveTo(x + xp[0], y + yp[0]);
    for (int i = 1; i < nPoints; i++)
      ip.lineTo(x + xp[i], y + yp[i]);
    ip.lineTo(x + xp[0], y + yp[0]);
    String s = IJ.d2s(count, 0);
    ip.moveTo(r.x + r.width / 2 - ip.getStringWidth(s) / 2, r.y + r.height / 2
        + 4);
    ip.setValue(1.0);
    ip.drawString(s);
  }

  void drawEllipse(ImageProcessor ip, ImageStatistics stats, int count) {
    stats.drawEllipse(ip);
  }

  void showResults() {
    int count = rt.getCounter();
    if (count == 0)
      return;
    boolean lastSlice = !processStack || slice == imp.getStackSize();
    if (displaySummary && lastSlice && rt == Analyzer.getResultsTable()
        && imp != null) {
      showSummary();
    }
    if (outlines != null && lastSlice) {
      String title = imp != null ? imp.getTitle() : "Outlines";
      String prefix = showChoice == MASKS ? "Mask of " : "Drawing of ";
      new ImagePlus(prefix + title, outlines).show();
    }

    // Comment by Laurent Jourdren

    /*
     * if (showResults && !processStack) { Analyzer.firstParticle =
     * beginningCount; Analyzer.lastParticle = Analyzer.getCounter() - 1; } else
     * Analyzer.firstParticle = Analyzer.lastParticle = 0;
     */
  }

  void showSummary() {
    String s = "";
    s += "Threshold: ";
    if ((int) level1 == level1 && (int) level2 == level2)
      s += (int) level1 + "-" + (int) level2 + "\n";
    else
      s += IJ.d2s(level1, 2) + "-" + IJ.d2s(level2, 2) + "\n";
    s += "Count: " + totalCount + "\n";
    float[] areas = rt.getColumn(ResultsTable.AREA);
    String aLine;
    if (areas != null) {
      double sum = 0.0;
      int start = areas.length - totalCount;
      if (start < 0)
        return;
      for (int i = start; i < areas.length; i++)
        sum += areas[i];
      int places = Analyzer.getPrecision();
      Calibration cal = imp.getCalibration();
      String unit = cal.getUnit();
      String total = IJ.d2s(sum, places);
      s += "Total Area: " + total + " " + unit + "^2\n";
      String average = IJ.d2s(sum / totalCount, places);
      s += "Average Size: " + IJ.d2s(sum / totalCount, places) + " " + unit
          + "^2\n";
      if (processStack)
        totalArea *= imp.getStackSize();
      String fraction = IJ.d2s(sum * 100.0 / totalArea, 2);
      s += "Area Fraction: " + fraction + "%";
      aLine = " " + "\t" + totalCount + "\t" + total + "\t" + average + "\t"
          + fraction;
    } else
      aLine = " " + "\t" + totalCount;
    if (tw != null) {
      tw.append("");
      tw.append(aLine);
    } else
      new TextWindow("Summary of " + imp.getTitle(), s, 300, 200);
  }

  int getColumnID(String name) {
    int id = rt.getFreeColumn(name);
    if (id == ResultsTable.COLUMN_IN_USE)
      id = rt.getColumnIndex(name);
    return id;
  }

  void makeCustomLut() {
    IndexColorModel cm = (IndexColorModel) LookUpTable
        .createGrayscaleColorModel(false);
    byte[] reds = new byte[256];
    byte[] greens = new byte[256];
    byte[] blues = new byte[256];
    cm.getReds(reds);
    cm.getGreens(greens);
    cm.getBlues(blues);
    reds[1] = (byte) 255;
    greens[1] = (byte) 0;
    blues[1] = (byte) 0;
    customLut = new IndexColorModel(8, 256, reds, greens, blues);
  }

  /** Called once when ImageJ quits. */
  public static void savePreferences(Properties prefs) {
    prefs.put(OPTIONS, Integer.toString(staticOptions));
  }

}
