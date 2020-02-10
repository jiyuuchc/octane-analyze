package edu.uchc.octane.analyze;

import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.measure.Calibration;

import java.awt.AWTEvent;
import java.awt.Rectangle;

/**
 * Base class for particle analysis dialogs that specify analysis parameters
 * @author Ji-Yu
 */
public abstract class ParticleAnalysisDialogBase extends NonBlockingGenericDialog {
	ImagePlus imp_; // the main image window
	Rectangle rect_; // selected region
	double pixelSize_; // best guess of the current pixel size

	ImageListener imageListener_;
	DialogListener dialogListener_;
	
	// monitor ImageJ window for changes
	class MyImageListener implements ImageListener {
		@Override
		public void imageClosed(ImagePlus imp) {
			if (imp == imp_) { dispose(); }
		}

		@Override
		public void imageOpened(ImagePlus imp) {}

		@Override
		public void imageUpdated(ImagePlus imp) {
			if (imp == imp_) { updateResults();	}
		}		
	}

	// update analysis result when there are changes in the dialog results 
	class MyDialogListener implements DialogListener{
		@Override
		public boolean dialogItemChanged(GenericDialog dlg, AWTEvent evt) {
			if (dlg != null) {
				// readParameters() return true if parameters are valid
				if (readParameters()) {
					updateResults();
				} else {
					imp_.killRoi();
					return false;
				}
			}
			return true;
		}
	};
	
	/**
	 * The dialog is non-modal. The analysis result of the current frame will be displayed in the form
	 * of a PointRoi. Parameter changes will trigger update of the analysis of current frame.
	 * Changes in the image window (e.g., change frame) will trigger update of the analysis. 
	 * @param imp The image data to be analyzed 
	 * @param title The title of the dialog
	 */
	public ParticleAnalysisDialogBase(ImagePlus imp, String title) {
		super(title);

		imp_ = imp;
		rect_ = imp.getProcessor().getRoi();
		pixelSize_ = retrievePixelSizeFromImage();
		
		imageListener_ = new MyImageListener(); 
		ImagePlus.addImageListener(imageListener_);
		
		dialogListener_ = new MyDialogListener();
		addDialogListener(dialogListener_);
	}

	double retrievePixelSizeFromImage() {
		Calibration c = imp_.getCalibration();
		double p = -1;
		
		if (c.pixelHeight == c.pixelWidth) {
			String unit = c.getUnit();
			if (unit.equalsIgnoreCase("nm")) {
				p = c.pixelHeight;
			} else if (unit.equalsIgnoreCase("micro")) {
				p = c.pixelHeight * 1000.0;
			}
		}
		
		if (p <= 0) {
			p = GlobalPrefs.defaultPixelSize_;
		}
		
		return p;
	}

	/* (non-Javadoc)
	 * @see java.awt.Window#dispose()
	 */
	@Override 
	public void dispose() {
		super.dispose();
		if (imageListener_ != null) {
			ImagePlus.removeImageListener(imageListener_);
			imageListener_ = null;
		}
		dialogListener_ = null;
	}
	
	/**
	 * Update the analysis of the current frame to display the PointRoi.
	 * The method starts a new analysis thread and returns immediately. Any new calls to the method 
	 * cancels previous analysis thread.  
	 */
	abstract void updateResults();
	
	/**
	 * @return True if parameters are valid, else False. 
	 */
	abstract boolean readParameters();
}
