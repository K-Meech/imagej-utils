/*-
 * #%L
 * TODO
 * %%
 * Copyright (C) 2018 - 2020 EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.embl.cba.tables.view;

import bdv.viewer.Source;
import customnode.CustomTriangleMesh;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.objects3d.FloodFill;
import de.embl.cba.tables.Logger;
import de.embl.cba.tables.Utils;
import de.embl.cba.tables.color.ColorUtils;
import de.embl.cba.tables.color.ColoringModel;
import de.embl.cba.tables.color.SelectionColoringModel;
import de.embl.cba.tables.ij3d.AnimatedViewAdjuster;
import de.embl.cba.tables.ij3d.UniverseUtils;
import de.embl.cba.tables.image.ImageSourcesModel;
import de.embl.cba.tables.imagesegment.ImageSegment;
import de.embl.cba.tables.mesh.MeshExtractor;
import de.embl.cba.tables.mesh.MeshUtils;
import de.embl.cba.tables.select.SelectionListener;
import de.embl.cba.tables.select.SelectionModel;
import ij3d.Content;
import ij3d.Image3DUniverse;
import ij3d.UniverseListener;
import isosurface.MeshEditor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.scijava.java3d.View;
import org.scijava.vecmath.Color3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static de.embl.cba.tables.Utils.getVoxelSpacings;

public class Segments3dView < T extends ImageSegment >
{
	private final List< T > segments;
	private final SelectionModel< T > selectionModel;
	private final SelectionColoringModel< T > selectionColoringModel;
	private final ImageSourcesModel imageSourcesModel;

	private Image3DUniverse universe;
	private T recentFocus;
	private double voxelSpacing3DView = 0; // 0 = auto
	private ConcurrentHashMap< T, Content > segmentToContent;
	private ConcurrentHashMap< Content, T > contentToSegment;
	private double transparency;
	private boolean isListeningToUniverse;
	private int meshSmoothingIterations;
	private int segmentFocusAnimationDurationMillis;
	private boolean contentModificationInProgress;
	private double segmentFocusZoomLevel;
	private double segmentFocusDxyMin;
	private double segmentFocusDzMin;
	private long maxNumSegmentVoxels;
	private String objectsName;
	private Component parentComponent;
	private boolean showSelectedSegmentsIn3D = true;
	private ConcurrentHashMap< T, CustomTriangleMesh > segmentToTriangleMesh;
	private ExecutorService executorService;
	private boolean forceUpdate = false;

	public Segments3dView(
			final List< T > segments,
			final SelectionModel< T > selectionModel,
			final SelectionColoringModel< T > selectionColoringModel,
			ImageSourcesModel imageSourcesModel )
	{
		this( segments,
				selectionModel,
				selectionColoringModel,
				imageSourcesModel,
				null );
	}

	public Segments3dView(
			final List< T > segments,
			final SelectionModel< T > selectionModel,
			final SelectionColoringModel< T > selectionColoringModel,
			ImageSourcesModel imageSourcesModel,
			Image3DUniverse universe )
	{
		this.segments = segments;
		this.selectionModel = selectionModel;
		this.selectionColoringModel = selectionColoringModel;
		this.imageSourcesModel = imageSourcesModel;
		this.universe = universe;

		this.transparency = 0.0;
		this.meshSmoothingIterations = 5;
		this.segmentFocusAnimationDurationMillis = 750;
		this.segmentFocusZoomLevel = 0.8;
		this.segmentFocusDxyMin = 20.0;
		this.segmentFocusDzMin = 20.0;
		this.voxelSpacing3DView = 0; // 0 = auto, using maxNumSegmentVoxels
		this.maxNumSegmentVoxels = 100 * 100 * 100;

		this.objectsName = "";

		this.executorService = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );

		this.segmentToContent = new ConcurrentHashMap<>();
		this.contentToSegment = new ConcurrentHashMap<>();

		registerAsSelectionListener( this.selectionModel );
		registerAsColoringListener( this.selectionColoringModel );
	}

	public void setObjectsName( String objectsName )
	{
		if ( objectsName == null )
			throw new RuntimeException( "Cannot set objects name in Segments3dView to null." );

		this.objectsName = objectsName;
	}

	public void setParentComponent( Component parentComponent )
	{
		this.parentComponent = parentComponent;
	}

	public void setVoxelSpacing3DView( double voxelSpacing3DView )
	{
		if ( this.voxelSpacing3DView != voxelSpacing3DView )
		{
			this.voxelSpacing3DView = voxelSpacing3DView;
			Logger.info( "3D View: voxel spacing changed => update all sources." );
			forceUpdate = true;
			updateAndShowSelectedSegments();
			forceUpdate = false;
		}
	}

	public void setTransparency( double transparency )
	{
		this.transparency = transparency;
	}

	public void setMeshSmoothingIterations( int iterations )
	{
		this.meshSmoothingIterations = iterations;
	}

	public void setSegmentFocusAnimationDurationMillis( int duration )
	{
		this.segmentFocusAnimationDurationMillis = duration;
	}

	public void setSegmentFocusZoomLevel( double segmentFocusZoomLevel )
	{
		this.segmentFocusZoomLevel = segmentFocusZoomLevel;
	}

	public void setSegmentFocusDxyMin( double segmentFocusDxyMin )
	{
		this.segmentFocusDxyMin = segmentFocusDxyMin;
	}

	public void setSegmentFocusDzMin( double segmentFocusDzMin )
	{
		this.segmentFocusDzMin = segmentFocusDzMin;
	}

	public void setMaxNumSegmentVoxels( long maxNumSegmentVoxels )
	{
		this.maxNumSegmentVoxels = maxNumSegmentVoxels;
	}

	public Image3DUniverse getUniverse()
	{
		return universe;
	}


	private void registerAsColoringListener( ColoringModel< T > coloringModel )
	{
		coloringModel.listeners().add( () -> adaptSegmentColors() );
	}

	private void adaptSegmentColors()
	{
		for ( T segment : segmentToContent.keySet() )
		{
			executorService.submit( () ->
			{
				final Color3f color3f = getColor3f( segment );
				final Content content = segmentToContent.get( segment );
				content.setColor( color3f );
			});
		}
	}

	public void registerAsSelectionListener( SelectionModel< T > selectionModel )
	{
		selectionModel.listeners().add( new SelectionListener< T >()
		{
			@Override
			public synchronized void selectionChanged()
			{
				if ( ! showSelectedSegmentsIn3D ) return;

				updateAndShowSelectedSegments();
			}

			@Override
			public synchronized void focusEvent( T selection )
			{
				if ( ! showSelectedSegmentsIn3D ) return;

				initUniverseAndListener();
				if ( universe.getContents().size() == 0 ) return;
				if ( selection == recentFocus ) return;
				if ( ! segmentToContent.containsKey( selection ) ) return;

				recentFocus = selection;

				final AnimatedViewAdjuster adjuster =
						new AnimatedViewAdjuster(
								universe,
								AnimatedViewAdjuster.ADJUST_BOTH );

				adjuster.apply(
						segmentToContent.get( selection ),
						30,
						segmentFocusAnimationDurationMillis,
						segmentFocusZoomLevel,
						segmentFocusDxyMin,
						segmentFocusDzMin );
			}
		} );
	}

	private synchronized void updateAndShowSelectedSegments()
	{
		contentModificationInProgress = true;
		showSelectedSegments();
		removeUnselectedSegments();
		contentModificationInProgress = false;
	}

	private void removeUnselectedSegments( )
	{
		final Set< T > selectedSegments = selectionModel.getSelected();
		final Set< T > currentSegments = segmentToContent.keySet();
		final Set< T > remove = new HashSet<>();

		for ( T segment : currentSegments )
			if ( ! selectedSegments.contains( segment ) )
				remove.add( segment );

		for( T segment : remove )
			removeSegmentFrom3DView( segment );
	}

	private synchronized void showSelectedSegments()
	{
		final Set< T > selected = selectionModel.getSelected();

		initUniverseAndListener();

		for ( T segment : selected )
		{
			if ( ! segmentToContent.containsKey( segment ) || forceUpdate )
			{
				if ( forceUpdate ) removeSegmentFrom3DView( segment );

				final CustomTriangleMesh mesh = getCustomTriangleMesh( segment, forceUpdate );
				if ( mesh != null )
					addMeshToUniverse( segment, mesh );
				else
					Logger.error( "3D View: Error creating mesh of segment " + segment.labelId() );
			}
		}
	}

	/**
	 * TODO: On Windows 10 this seems to throw an error
	 *
	 * @param segments
	 */
	private synchronized void showSelectedSegmentsMultiThreaded( Set< T > segments )
	{
		initUniverseAndListener();

		final ArrayList< Future > futures = new ArrayList<>();
		for ( T segment : segments )
			if ( ! segmentToContent.containsKey( segment ) )
				futures.add(
						executorService.submit( () ->
								{
									final CustomTriangleMesh mesh = getCustomTriangleMesh( segment, forceUpdate );
									if ( mesh != null )
										addMeshToUniverse( segment, mesh  );
									else
										Logger.info( "Error creating mesh of segment " + segment.labelId() );
								}
						) );

		Utils.fetchFutures( futures );
	}

	private synchronized void removeSegmentFrom3DView( T segment )
	{
		final Content content = segmentToContent.get( segment );
		if ( content != null && universe != null )
		{
			universe.removeContent( content.getName() );
			segmentToContent.remove( segment );
			contentToSegment.remove( content );
		}
	}

	private CustomTriangleMesh getCustomTriangleMesh( T segment, boolean forceUpdate )
	{
		CustomTriangleMesh triangleMesh = getTriangleMesh( segment, forceUpdate );
		if ( triangleMesh == null ) return null;
		MeshEditor.smooth2( triangleMesh, meshSmoothingIterations );
		return triangleMesh;
	}

	private CustomTriangleMesh getTriangleMesh( T segment, boolean forceUpdate )
	{
		if ( segment.getMesh() == null || forceUpdate )
		{
			final float[] mesh = createMesh( segment );
			if ( mesh == null ) return null;
			else segment.setMesh( mesh );
		}

		CustomTriangleMesh triangleMesh = MeshUtils.asCustomTriangleMesh( segment.getMesh() );
		triangleMesh.setColor( getColor3f( segment ) );
		return triangleMesh;
	}

	private float[] createMesh( ImageSegment segment )
	{
		final Source< ? > labelsSource = imageSourcesModel.sources().get( segment.imageId() ).source();

		Integer level = getLevel( segment, labelsSource );
		if ( level == null ) return null;

		final double[] voxelSpacing = Utils.getVoxelSpacings( labelsSource ).get( level );

		Logger.info( "3D View: Fetching source " + labelsSource.getName() + " at resolution " + voxelSpacing[ level ] + " micrometer..." );

		final RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI = getLabelsRAI( segment, level );

		if ( segment.boundingBox() == null )
			setSegmentBoundingBox( segment, labelsRAI, voxelSpacing );

		FinalInterval boundingBox = getIntervalVoxels( segment.boundingBox(), voxelSpacing );

		final long numElements = Intervals.numElements( boundingBox );

		if ( voxelSpacing3DView == 0 )
		{
			if ( numElements > maxNumSegmentVoxels )
			{
				Logger.error( "3D View:\n" +
						"The bounding box of the selected segment has " + numElements + " voxels.\n" +
						"The maximum enabled number is " + maxNumSegmentVoxels + ".\n" +
						"Thus the image segment will not be displayed in 3D." );
				return null;
			}
		}

//		ImageJFunctions.show( ( RandomAccessibleInterval ) Views.interval( labelsRAI, boundingBox ) );

		final MeshExtractor meshExtractor = new MeshExtractor(
				Views.extendZero( ( RandomAccessibleInterval ) labelsRAI ),
				boundingBox,
				new AffineTransform3D(),
				new int[]{ 1, 1, 1 },
				() -> false );

		final float[] meshCoordinates = meshExtractor.generateMesh( segment.labelId() );

		for ( int i = 0; i < meshCoordinates.length; )
		{
			meshCoordinates[ i++ ] *= voxelSpacing[ 0 ];
			meshCoordinates[ i++ ] *= voxelSpacing[ 1 ];
			meshCoordinates[ i++ ] *= voxelSpacing[ 2 ];
		}

		if ( meshCoordinates.length == 0 )
		{
			Logger.warn( "Could not find any pixels for segment with label " + segment.labelId()
					+ "\nwithin bounding box " + boundingBox );
			return null;
		}

		return meshCoordinates;
	}

	private Integer getLevel( ImageSegment segment, Source< ? > labelsSource )
	{
		Integer resolutionLevel;

		if ( voxelSpacing3DView != 0 )
		{
			resolutionLevel = UniverseUtils.getLevel( labelsSource, voxelSpacing3DView );
		}
		else // auto-resolution
		{
			if ( segment.boundingBox() == null )
			{
				Logger.error( "3D View:\n" +
						"Automated resolution level selection is enabled, but the segment has no bounding box.\n" +
						"This combination is currently not possible." );
				resolutionLevel = null;
			}
			else
			{
				final ArrayList< double[] > voxelSpacings = Utils.getVoxelSpacings( labelsSource );

				for ( resolutionLevel = 0; resolutionLevel < voxelSpacings.size(); resolutionLevel++ )
				{
					FinalInterval boundingBox = getIntervalVoxels( segment.boundingBox(), voxelSpacings.get( resolutionLevel ) );

					final long numElements = Intervals.numElements( boundingBox );

					if ( numElements <= maxNumSegmentVoxels )
						break;
				}
			}
		}


		return resolutionLevel;
	}

	public boolean getShowSelectedSegmentsIn3D () {
		return this.showSelectedSegmentsIn3D;
	}

	public synchronized void setShowSelectedSegmentsIn3D( boolean showSelectedSegmentsIn3D )
	{
		this.showSelectedSegmentsIn3D = showSelectedSegmentsIn3D;

		if ( showSelectedSegmentsIn3D )
		{
			updateAndShowSelectedSegments();
		}
		else
		{
			removeSelectedSegmentsFromView();
		}

	}

	private void removeSelectedSegmentsFromView()
	{
		final Set< T > selected = selectionModel.getSelected();

		for ( T segment : selected )
		{
			removeSegmentFrom3DView( segment );
		}
	}

	private void setSegmentBoundingBox(
			ImageSegment segment,
			RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI,
			double[] voxelSpacing )
	{
		final long[] voxelCoordinate =
				getSegmentCoordinateVoxels( segment, voxelSpacing );

		final FloodFill floodFill = new FloodFill(
				labelsRAI,
				new DiamondShape( 1 ),
				1000 * 1000 * 1000L );

		floodFill.run( voxelCoordinate );
		final RandomAccessibleInterval mask = floodFill.getCroppedRegionMask();

		final int numDimensions = segment.numDimensions();
		final double[] min = new double[ numDimensions ];
		final double[] max = new double[ numDimensions ];
		for ( int d = 0; d < numDimensions; d++ )
		{
			min[ d ] = mask.min( d ) * voxelSpacing[ d ];
			max[ d ] = mask.max( d ) * voxelSpacing[ d ];
		}

		segment.setBoundingBox( new FinalRealInterval( min, max ) );
	}

	private long[] getSegmentCoordinateVoxels(
			ImageSegment segment,
			double[] calibration )
	{
		final long[] voxelCoordinate = new long[ segment.numDimensions() ];
		for ( int d = 0; d < segment.numDimensions(); d++ )
			voxelCoordinate[ d ] = ( long ) (
					segment.getDoublePosition( d ) / calibration[ d ] );
		return voxelCoordinate;
	}

	private FinalInterval getIntervalVoxels(
			FinalRealInterval realInterval,
			double[] calibration )
	{
		final long[] min = new long[ 3 ];
		final long[] max = new long[ 3 ];
		for ( int d = 0; d < 3; d++ )
		{
			min[ d ] = (long) ( realInterval.realMin( d )
					/ calibration[ d ] );
			max[ d ] = (long) ( realInterval.realMax( d )
					/ calibration[ d ] );
		}
		return new FinalInterval( min, max );
	}

	private RandomAccessibleInterval< ? extends RealType< ? > >
	getLabelsRAI( ImageSegment segment, int level )
	{
		final Source< ? > labelsSource
				= imageSourcesModel.sources().get( segment.imageId() ).source();

		final RandomAccessibleInterval< ? extends RealType< ? > > rai =
				BdvUtils.getRealTypeNonVolatileRandomAccessibleInterval(
						labelsSource, 0, level );

		return rai;
	}

	private void addMeshToUniverse( T segment, CustomTriangleMesh mesh )
	{
		if ( mesh == null )
			throw new RuntimeException( "Mesh of segment " + objectsName + "_" + segment.labelId() + " is null." );

		if ( universe == null )
			throw new RuntimeException( "Universe is null." );

		final Content content = universe.addCustomMesh( mesh, objectsName + "_" + segment.labelId() );

		content.setTransparency( ( float ) transparency );
		content.setLocked( true );

		segmentToContent.put( segment, content );
		contentToSegment.put( content, segment );

		universe.setAutoAdjustView( false );
	}

	public synchronized void initUniverseAndListener()
	{
		if ( universe == null )
			universe = new Image3DUniverse();

		UniverseUtils.showUniverseWindow( universe, parentComponent );

		if ( ! isListeningToUniverse )
			isListeningToUniverse = addUniverseListener();
	}

	private boolean addUniverseListener()
	{
		universe.addUniverseListener( new UniverseListener()
		{

			@Override
			public void transformationStarted( View view )
			{

			}

			@Override
			public void transformationUpdated( View view )
			{

				// TODO: maybe try to synch this with the Bdv View

				//				final Transform3D transform3D = new Transform3D();
//			view.getUserHeadToVworld( transform3D );

//				final Transform3D transform3D = new Transform3D();
//			universe.getVworldToCamera( transform3D );
//				System.out.println( transform3D );

//				final Transform3D transform3DInverse = new Transform3D();
//				universe.getVworldToCameraInverse( transform3DInverse );
//				System.out.println( transform3DInverse );

//				final TransformGroup transformGroup =
//						universe.getViewingPlatform()
//								.getMultiTransformGroup().getTransformGroup(
//										DefaultUniverse.ZOOM_TG );
//
//				final Transform3D transform3D = new Transform3D();
//				transformGroup.getTransform( transform3D );
//
//				System.out.println( transform3D );
			}

			@Override
			public void transformationFinished( View view )
			{

			}

			@Override
			public void contentAdded( Content c )
			{

			}

			@Override
			public void contentRemoved( Content c )
			{

			}

			@Override
			public void contentChanged( Content c )
			{

			}

			@Override
			public void contentSelected( Content c )
			{
				if ( c == null ) return;

				if ( ! contentToSegment.containsKey( c ) )
					return;

				final T segment = contentToSegment.get( c );

				if ( selectionModel.isFocused( segment ) )
					return;
				else
				{
					recentFocus = segment; // This avoids "self-focusing"
					selectionModel.focus( segment );
				}

			}

			@Override
			public void canvasResized()
			{

			}

			@Override
			public void universeClosed()
			{

			}
		} );

		return true;
	}

	private Color3f getColor3f( T imageSegment )
	{
		final ARGBType argbType = new ARGBType();
		selectionColoringModel.convert( imageSegment, argbType );
		return new Color3f( ColorUtils.getColor( argbType ) );
	}


	public void close()
	{
		// TODO
	}
}
