package edu.uchc.octane.analyze;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

public class TestPlugin {
	public static void main(String ... args) {
        ImageJ imagej = new ImageJ();
        //ImagePlus imp = IJ.openImage();
        //imp.show();
        AnalyzePlugin plugin = new AnalyzePlugin();
        plugin.run("quickload");
	}
}
