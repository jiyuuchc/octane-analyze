package edu.uchc.octane.analyze;

import ij.plugin.FileInfoVirtualStack;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.IJ;
import ij.ImagePlus;
import ij.io.DirectoryChooser;

import java.io.IOException;

import org.json.JSONException;

import edu.uchc.octane.core.utils.MMTaggedTiff;
import edu.uchc.octane.core.utils.TaggedImage;

public class MMVirtualStack extends FileInfoVirtualStack {
	private String pathname;
	private int nFrames;
	private int width, height;
	private MMTaggedTiff stackReader;

	private void init(String dir) throws IOException, JSONException {
		this.pathname = dir;
		this.stackReader = new MMTaggedTiff(dir, false, false);
		this.nFrames = stackReader.getSummaryMetadata().getInt("Frames");
		TaggedImage curImg= stackReader.getImage(0 /*channel*/, 0 /*slice*/, 0 /*frame*/, 0 /*position*/);
		width = curImg.tags.getInt("Width");
		short [] pix = (short []) curImg.pix;
		height = pix.length / width;
		setBitDepth(16);
	}
	
	private ImagePlus open() {
		ImagePlus imp = new ImagePlus();
		imp.setStack("Whatever", this);
		imp.setDimensions(1, 1, size());
		return imp;
	}

	public MMVirtualStack() {}

	public MMVirtualStack(String pathname) throws JSONException, IOException {
		init(pathname);
	}

	@Override
	public void run(String arg) {
		DirectoryChooser dc = new DirectoryChooser("Select Data");
		String  dir = dc.getDirectory();
		if (dir == null)
			return;
		try {
			init(dir);
		} catch (IOException | JSONException e) {
			IJ.error(e.getLocalizedMessage());
			return;
		}

		ImagePlus imp = open();
		if (imp!=null)
			imp.show();		
	}

	@Override
	public String getDirectory() {
		return pathname;
	}
	
	@Override
	public int getHeight() {
		return height;
	}
	
	@Override
	public int getWidth() {
		return width;
	}
	
	@Override
	public int size() {
		return getSize();
	}

	@Override
	public int getSize() {
		return nFrames;
	}
	
	@Override
	public String getSliceLabel(int n) {
		return null;
	}
	
	@Override
	public ImageProcessor getProcessor(int n) {
		n = translate(n);
		if (n < 1 || n > nFrames)
			throw new IllegalArgumentException("Frame number out of range.");

		TaggedImage img= stackReader.getImage(0 /*channel*/, 0 /*slice*/, n-1 /*frame*/, 0 /*position*/);
		if (img == null) {
			IJ.error("Error reading Frame " + n);
			return null;
		}
		short [] data = (short []) img.pix; 

		return new ShortProcessor(width, height, data, null);
	}
}
