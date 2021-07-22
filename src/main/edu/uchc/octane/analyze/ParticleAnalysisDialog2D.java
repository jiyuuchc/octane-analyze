package edu.uchc.octane.analyze;

import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import javax.swing.JFileChooser;

import edu.uchc.octane.core.datasource.OctaneDataFile;
import edu.uchc.octane.core.fitting.Fitter;
import edu.uchc.octane.core.fitting.leastsquare.DAOFitting;
import edu.uchc.octane.core.fitting.leastsquare.IntegratedGaussianPSF;
import edu.uchc.octane.core.fitting.leastsquare.LeastSquare;
import edu.uchc.octane.core.fitting.maximumlikelihood.PoissonLogLikelihoodSymmetric;
import edu.uchc.octane.core.fitting.maximumlikelihood.Simplex;
import edu.uchc.octane.core.frameanalysis.LocalMaximum;
import edu.uchc.octane.core.pixelimage.RectangularImage;
import edu.uchc.octane.core.pixelimage.RectangularShortImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.gui.Roi;

public class ParticleAnalysisDialog2D extends ParticleAnalysisDialogBase {

	List<double[]> [] results_;
	volatile Thread prevProcess_ = null;
	
	int kernelSize_;
	double sigma_;
	Roi roi_;

	// prefs
	static Preferences prefs_ = null;
	boolean multiPeakFitting_;
	// boolean preProcessBackground_;
	// int watershedThreshold_;
	int watershedNoise_;
	//double heightMin_;
	//double fittingQualityMin_;
	double resolution_;
	final private static String IMAGE_RESOLUTION = "imageResolution";
	final private static String MULTI_PEAK_FITTING_KEY = "multiPeakFitting";
	//final private static String ZERO_BACKGROUND_KEY = "zeroBackground";
	//final private static String WATERSHED_THRESHOLD_KEY = "threshold";
	final private static String WATERSHED_NOISE_KEY = "noise";
	//final private static String HEIGHT_MIN_KEY = "minHeight";
	//final private static String FITTING_QUALITY_MIN_KEY = "minFittingQ";

	//Detect and mark particles in current frame
	class MarkParticles extends Thread {
		List<double[]> particles;

		void showRoi() {
			imp_.killRoi();

			if (particles.size() > 0) {
				PointRoi roi;
				int [] xi = new int[particles.size()];
				int [] yi = new int[particles.size()];
				for (int i = 0; i < particles.size(); i ++ ) {
					xi[i] = (int) (particles.get(i)[0] + 0.5);
					yi[i] = (int) (particles.get(i)[1] + 0.5);
				}
				roi = new PointRoi(xi, yi, particles.size());
				roi.setOptions("dot");
				imp_.setRoi(roi);
			}			
		}

		@Override
		public void run() {
			particles = new ArrayList<double []>();
			short [] pixels = (short[]) imp_.getProcessor().convertToShortProcessor().getPixels();
			RectangularShortImage img = new RectangularShortImage(pixels, imp_.getWidth());			

			analyzeOneFrame(img, particles);			

			showRoi();
		}
	}

	/**
	 * Constructor
	 * @param imp The image to be analyzed
	 */
	public ParticleAnalysisDialog2D(ImagePlus imp) {
		super(imp, "Particle analysis:" + imp.getTitle());
		roi_ = imp.getRoi();
		setupDialog();
	}

	/* (non-Javadoc)
	 * @see ij.gui.NonBlockingGenericDialog#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed(ActionEvent ev) {
		super.actionPerformed(ev);
		if (wasOKed()) {
			savePrefs();
			JFileChooser jc = new JFileChooser();
			if (jc.showSaveDialog(IJ.getApplet()) == JFileChooser.APPROVE_OPTION) {
				OctaneDataFile dataset = processAll();
 				if (dataset != null) {
 					try {
						dataset.writeToFile(jc.getSelectedFile().getPath());
					} catch (IOException err) {
						IJ.error("Error saving data", err.getMessage());
					}
 				}
			}
		}
		imp_.setRoi(roi_);
	}
	
	public OctaneDataFile processAll() {
		if (imp_ == null) {return null;}
		
		imp_.killRoi();
		IJ.log("Analyzing particles");
		
		final ImageStack stack = imp_.getImageStack();
		int numOfFrames = stack.getSize();

		results_ = (ArrayList<double []>[])new ArrayList[numOfFrames];
		for (int i = 0; i < numOfFrames; i++) {
			results_[i] = new ArrayList<double[]>();
		}

		IntStream.range(1, numOfFrames + 1).parallel().forEach( frameNumber -> {
			short [] pixels;
			synchronized(imp_) {
				pixels = (short[]) stack.getProcessor(frameNumber).convertToShortProcessor().getPixels();
			}
			RectangularShortImage img = new RectangularShortImage(pixels, imp_.getWidth());
			analyzeOneFrame(img, results_[frameNumber - 1]);
			IJ.log("Process frame " + (frameNumber)+ " to obtain " + (results_[frameNumber - 1].size()) + " particles.");
		});
		
		//count total particles
		int cnt = 0;
		for (int i = 0; i < stack.getSize(); i++) {
			cnt += results_[i].size();
		}
		
		// convert the results to 2D array and LocalizationDataset object
		String [] tmpHeaders = new IntegratedGaussianPSF().getHeaders();
		String [] headers = Arrays.copyOf(tmpHeaders, tmpHeaders.length+1);
		headers[headers.length-1] = "frame";

		double [][] data = new double[headers.length][cnt];
		int idx = 0;
		for (int i = 0; i < stack.getSize(); i++) {
			List<double[]> curFrame = results_[i];
			for (int j = 0; j < curFrame.size(); j++) {
				data[headers.length-1][idx] = i + 1; //frame
				double [] param = curFrame.get(j);
				for (int k = 0; k < headers.length - 1; k++) {
					String s = headers[k]; 
					if ( s.equals("x") || s.equals("y") || s.equals("z") || s.startsWith("sigma")) {
						data[k][idx] = curFrame.get(j)[k] * pixelSize_;
					} else {
						data[k][idx] = curFrame.get(j)[k];
					}					
				}
				idx++;
			}
		}
		return new OctaneDataFile(data, headers);
	}

	public void loadPrefs() {
		if (prefs_ == null) {
			prefs_ = GlobalPrefs.getRoot().node(this.getClass().getName());
		}

		resolution_ =  prefs_.getDouble(IMAGE_RESOLUTION, 300);
		multiPeakFitting_ = prefs_.getBoolean(MULTI_PEAK_FITTING_KEY, false);
		//preProcessBackground_ = prefs_.getBoolean(ZERO_BACKGROUND_KEY, false);
		//watershedThreshold_ = prefs_.getInt(WATERSHED_THRESHOLD_KEY, 100);
		watershedNoise_ = prefs_.getInt(WATERSHED_NOISE_KEY, 100);
		//heightMin_ = prefs_.getDouble(HEIGHT_MIN_KEY, -1);
		//fittingQualityMin_ = prefs_.getDouble(FITTING_QUALITY_MIN_KEY, -1);			
	}

	/**
	 * Save parameters to persistent store
	 */
	public void savePrefs() {
		if (prefs_ == null) {
			return;
		}
		prefs_.putDouble(IMAGE_RESOLUTION, resolution_);
		prefs_.putBoolean(MULTI_PEAK_FITTING_KEY, multiPeakFitting_);
		// prefs_.putBoolean(ZERO_BACKGROUND_KEY, preProcessBackground_);
		//prefs_.putInt(WATERSHED_THRESHOLD_KEY, watershedThreshold_);
		prefs_.putInt(WATERSHED_NOISE_KEY, watershedNoise_);
		//prefs_.putDouble(HEIGHT_MIN_KEY, heightMin_);
		//prefs_.putDouble(FITTING_QUALITY_MIN_KEY, fittingQualityMin_);
	}

	void setupDialog() { 

		loadPrefs();
		
		addNumericField("Pixel Size (nm)", pixelSize_, 0);
		addNumericField("Image Resolution (FWHM) (nm)", resolution_, 1);
		addCheckbox("High Molecular Density", multiPeakFitting_);
		//addSlider("Intensity Threshold", 1, 40000.0, watershedThreshold_);
		addSlider("Noise Threshold", 1, 5000.0, watershedNoise_);
		//addSlider("Minimum Intensity", 0, 5000.0, heightMin_);
		//addSlider("Minimum Fitting Quality", 0, 100, fittingQualityMin_);

		Vector<Scrollbar> sliders = (Vector<Scrollbar>)getSliders();
		sliders.get(0).setUnitIncrement(20); // default was 1
		// sliders.get(1).setUnitIncrement(5); // default was 1
	}

	@Override
	public boolean readParameters() {
		pixelSize_ = getNextNumber();
		resolution_ = getNextNumber();
		multiPeakFitting_ = (boolean) getNextBoolean();
		//watershedThreshold_ = (int) getNextNumber();
		watershedNoise_ = (int) getNextNumber();
		//heightMin_ = getNextNumber();
		//fittingQualityMin_ = getNextNumber();
		
		sigma_ = resolution_ / 2.355 / pixelSize_;		
		kernelSize_ = (int) Math.round(sigma_ * 2.5);

		if (kernelSize_ < 1 || kernelSize_ > 15 || watershedNoise_ <= 0 ) {
			return false;
		} else {
			return true;
		}
	}

	String [] analyzeOneFrame(RectangularImage img, final List<double []> particles) {
		LocalMaximum finder = new LocalMaximum(watershedNoise_, 0, kernelSize_);
		final Fitter fitter;
		if (! multiPeakFitting_) {
			//fitter = new LeastSquare(new IntegratedGaussianPSF());
			fitter = new Simplex(new PoissonLogLikelihoodSymmetric(100, 2.5));
		} else {
			fitter = new DAOFitting(new IntegratedGaussianPSF());
		}

		finder.processFrame(img, new LocalMaximum.CallBackFunctions() {
			@Override
			public boolean fit(RectangularImage img, int x, int y) {
				// System.out.println("Location " + (c++) +" : " + x + " - " + y + " - " + img.getValueAtCoordinate(x, y));

				// false stops further processing
				if (Thread.interrupted()) {return false;}

				if (roi_ == null || roi_.contains(x, y)) {
					double [] result = fitter.fit(img, null);
					while (result != null ) {
						particles.add(result);
						if (multiPeakFitting_) {
							result = ((DAOFitting)fitter).getNextResult();
						} else {
							result = null; // no next result unless it's multipeakFitting
						}
					}
				}
				return true;
			}	
		});
		
		return fitter.getHeaders();
	}

	@Override
	void updateResults() {
		if (imp_ == null) { return;	}
		imp_.killRoi();

		// only allow one updating thread
		synchronized(this) {
			if (prevProcess_ != null && prevProcess_.isAlive()) {
				prevProcess_.interrupt();
			}	
			prevProcess_ = new MarkParticles();
			prevProcess_.start();
		}
	}
}
