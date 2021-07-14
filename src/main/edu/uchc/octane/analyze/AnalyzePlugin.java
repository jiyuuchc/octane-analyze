package edu.uchc.octane.analyze;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.UIManager;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

/**
 * The PlugIn adaptor.
 *
 */
public class AnalyzePlugin implements PlugIn{

	ImagePlus imp_;
	ParticleAnalysisDialogBase dlg_;
	public AnalyzePlugin() {
		try {
			
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

		} catch (Exception e) {
		
			System.err.println(e.getMessage());

		}
	}

//	/**
//	 * Display particle analysis dialog and start particle analysis
//	 * @return True if user clicked OK 
//	 */
//	boolean startImageAnalysis(String cmd) {

		/*
//		dict_.put(imp_, null);
		
		if (cmd.equals("analyze2D")) {

			dlg_ = new ParticleAnalysisDialog2D(imp_);

		} else if (cmd.equals("analyze3D")){
		
//			dlg_ = new ParticleAnalysisDialogAstigmatism(imp_);

		} else if (cmd.equals("calibration")) {
			
//			dlg_ = new CalibrationDialogAstigmatism(imp_);

		} else {	
			
			return false;
			
		}
*/		

	void linkDialogWithImage() {
		// the iconification state of analysis dialog follows the main image window  
		imp_.getWindow().addWindowListener(new WindowAdapter() {
			
			boolean wasVisible;
			
			@Override
			public void windowIconified(WindowEvent e) {			
				wasVisible = dlg_.isVisible();
				dlg_.setVisible(false);
			}
	
			@Override
			public void windowDeiconified(WindowEvent e) {			
				if (dlg_.isDisplayable()) {
					dlg_.setVisible(wasVisible);
				}
			}
	
			@Override
			public void windowClosed(WindowEvent e) {
				dlg_.dispose();
			}
		});
	}

	@Override
	public void run(String cmd) {
		
		if (!IJ.isJava16()) {		
			IJ.error("Octane requires Java version 1.6 or higher. Please upgrade the JVM.");
			return;
		}

		if (cmd.equals("prefs")) {
			GlobalPrefs.openDialog(true);
			return;
		}
		
		if (cmd.equals("quickload")) {
			MMVirtualStack plugin = new MMVirtualStack();
			plugin.run(null);
			return;
		}

		imp_ = WindowManager.getCurrentImage();		
		if (imp_ == null || imp_.getStack().getSize() < 2) {
			IJ.error("This only works on an opened image stack.");
			return;
		}

		// FileInfo fi = imp_.getOriginalFileInfo();

//		if (cmd.startsWith("analyze") || cmd.startsWith("calibration")) {			
//			startImageAnalysis(cmd);
//		}
		
		if (cmd.equals("analyze2D")) {
			dlg_ = new ParticleAnalysisDialog2D(imp_);
		} 
		
		linkDialogWithImage();
		
		dlg_.showDialog();
	}
}
