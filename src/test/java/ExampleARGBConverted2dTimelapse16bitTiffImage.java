import bdv.util.*;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class ExampleARGBConverted2dTimelapse16bitTiffImage
{
	public static < T extends RealType< T > > void main ( String[] args )
	{
		final ImagePlus imagePlus = IJ.openImage( ExampleARGBConverted2dTimelapse16bitTiffImage.class.getResource( "2d-timelapse-16bit-labelMask.tif" ).getFile() );

		RandomAccessibleInterval< T > wrap = ImageJFunctions.wrapReal( imagePlus );

		// needs to be 3D
		wrap = Views.addDimension( wrap, 0, 0);
		// make time last dimension
		wrap = Views.permute( wrap, 3,2 );

		final RandomAccessibleIntervalSource4D raiSource
				= new RandomAccessibleIntervalSource4D(
						wrap,
						Util.getTypeFromInterval( wrap ),
						imagePlus.getTitle() );


		final boolean present = raiSource.isPresent( 0 );
		final boolean present1 = raiSource.isPresent( 1 );
		final boolean present2 = raiSource.isPresent( 2 );

		/**
		 * Show the gray-scale image
		 */

		// TODO: it does not show the timepoints..
		// https://github.com/bigdataviewer/bigdataviewer-vistools/blob/master/src/main/java/bdv/util/BdvFunctions.java#L173
		// add issue:
		//
		BdvFunctions.show( raiSource, BdvOptions.options().is2D() ).setDisplayRange( 0, 3 );


		/**
		 * Show as ARGB image
		 */

//		final ConfigurableRealVolatileARGBConverter converter = new ConfigurableRealVolatileARGBConverter();
//
//		final VolatileARGBConvertedRealSource labelsSource = new VolatileARGBConvertedRealSource( raiSource, "test", converter );
//
//		BdvFunctions.show( labelsSource,
//				BdvOptions.options().is2D() );
	}
}