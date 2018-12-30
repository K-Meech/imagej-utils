import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.RandomAccessibleIntervalSource;
import de.embl.cba.bdv.utils.argbconversion.VolatileARGBConvertedRealSource;
import de.embl.cba.bdv.utils.argbconversion.SelectableRealVolatileARGBConverter;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class TestARGBConverted2d16bitTiffImage
{
	public static < T extends RealType< T > > void main ( String[] args )
	{
		final ImagePlus imagePlus = IJ.openImage( TestARGBConverted2d16bitTiffImage.class.getResource( "2d-16bit-labelMask.tif" ).getFile() );

		RandomAccessibleInterval< T > wrap = ImageJFunctions.wrapReal( imagePlus );

		// needs to be at least 3D
		wrap = Views.addDimension( wrap, 0, 0);

		final RandomAccessibleIntervalSource raiSource = new RandomAccessibleIntervalSource( wrap, Util.getTypeFromInterval( wrap ), imagePlus.getTitle() );


		/**
		 * Show as gray-scale image
		 */

//		BdvFunctions.show( raiSource, BdvOptions.options().is2D() ).setDisplayRange( 0, 3 );


		/**
		 * Show as ARGB image
		 */

		final SelectableRealVolatileARGBConverter converter = new SelectableRealVolatileARGBConverter();

		final VolatileARGBConvertedRealSource labelsSource = new VolatileARGBConvertedRealSource( raiSource, converter );

		BdvFunctions.show( labelsSource, BdvOptions.options().is2D() );

	}
}