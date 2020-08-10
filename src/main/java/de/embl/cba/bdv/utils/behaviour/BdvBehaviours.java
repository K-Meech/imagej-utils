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
package de.embl.cba.bdv.utils.behaviour;

import bdv.ij.util.ProgressWriterIJ;
import bdv.tools.boundingbox.TransformedRealBoxSelectionDialog;
import bdv.util.BdvHandle;
import de.embl.cba.bdv.utils.BdvDialogs;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.Logger;
import de.embl.cba.bdv.utils.bigwarp.BigWarpLauncher;
import de.embl.cba.bdv.utils.capture.BdvViewCaptures;
import de.embl.cba.bdv.utils.capture.ViewCaptureDialog;
import de.embl.cba.bdv.utils.export.BdvRealSourceToVoxelImageExporter;
import ij.IJ;
import net.imglib2.FinalRealInterval;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import javax.swing.*;
import java.util.List;

import static de.embl.cba.bdv.utils.export.BdvRealSourceToVoxelImageExporter.*;

// TODO:
// - remove logging, return things

public class BdvBehaviours
{
	public static void addPositionAndViewLoggingBehaviour(
			BdvHandle bdv,
			Behaviours behaviours,
			String trigger )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) -> {

			(new Thread( () -> {
				Logger.log( "\nBigDataViewer position: " + BdvUtils.getGlobalMousePositionString( bdv ) );
				Logger.log( "BigDataViewer transform: " + BdvUtils.getBdvViewerTransformString( bdv ) );
			} )).start();

		}, "Print position and view", trigger ) ;
	}

	public static void addViewCaptureBehaviour(
			BdvHandle bdvHandle,
			Behaviours behaviours,
			String trigger )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			new Thread( () -> {
				new ViewCaptureDialog( bdvHandle ).run();
			}).start();
		}, "capture raw view", trigger ) ;
	}

	public static void addSimpleViewCaptureBehaviour(
			BdvHandle bdv,
			Behaviours behaviours,
			String trigger )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			new Thread( () -> {
				SwingUtilities.invokeLater( () -> {
					final JFileChooser jFileChooser = new JFileChooser();
					if ( jFileChooser.showSaveDialog( bdv.getViewerPanel() ) == JFileChooser.APPROVE_OPTION )
					{
						BdvViewCaptures.saveScreenShot(
								jFileChooser.getSelectedFile(),
								bdv.getViewerPanel() );
					}
				});
			}).start();

		}, "capture simple view", trigger ) ;
	}


	public static void addExportSourcesToVoxelImagesBehaviour(
			BdvHandle bdvHandle,
			Behaviours behaviours,
			String trigger )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			new Thread( () ->
			{
				final FinalRealInterval maximalRangeInterval = BdvUtils.getRealIntervalOfVisibleSources( bdvHandle );

				final TransformedRealBoxSelectionDialog.Result result =
						BdvDialogs.showBoundingBoxDialog(
								bdvHandle,
								maximalRangeInterval );

				BdvUtils.getVoxelDimensionsOfCurrentSource( bdvHandle ).dimensions( Dialog.outputVoxelSpacings );

				if ( ! Dialog.showDialog() ) return;

				final BdvRealSourceToVoxelImageExporter exporter =
						new BdvRealSourceToVoxelImageExporter(
								bdvHandle,
								BdvUtils.getVisibleSourceIndices( bdvHandle ),
								result.getInterval(),
								result.getMinTimepoint(),
								result.getMaxTimepoint(),
								Dialog.interpolation,
								Dialog.outputVoxelSpacings,
								Dialog.exportModality,
								Dialog.exportDataType,
								Dialog.numProcessingThreads,
								new ProgressWriterIJ()
						);

				if ( Dialog.exportModality.equals( ExportModality.SaveAsTiffVolumes ) )
				{
					final String outputDirectory = IJ.getDirectory( "Choose and output directory" );
					exporter.setOutputDirectory( outputDirectory );
				}

				exporter.export();

			}).start();
		}, "ExportSourcesToVoxelImages", trigger ) ;
	}

	public static void addAlignSourcesWithBigWarpBehaviour(
			BdvHandle bdvHandle,
			Behaviours behaviours,
			String trigger )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			new Thread( () ->
			{
				final List< Integer > sourceIndices = BdvUtils.getSourceIndiciesVisibleInCurrentViewerWindow( bdvHandle, true );

				if ( ! BigWarpLauncher.Dialog.showDialog( bdvHandle, sourceIndices ) ) return;
				new BigWarpLauncher(
						bdvHandle,
						BigWarpLauncher.Dialog.movingVolatileSource,
						BigWarpLauncher.Dialog.fixedVolatileSource,
						BigWarpLauncher.Dialog.displayRangeMovingSource,
						BigWarpLauncher.Dialog.displayRangeFixedSource
				);

			}).start();
		}, "AlignSourcesWithBigWarp", trigger ) ;
	}

	public static void addDisplaySettingsBehaviour(
			BdvHandle bdv,
			Behaviours behaviours,
			String trigger )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
						BdvDialogs.showDisplaySettingsDialogForSourcesAtMousePosition(
								bdv,
								false,
								true ),
				"show display settings dialog",
				trigger ) ;
	}

	public static void addSourceBrowsingBehaviour( BdvHandle bdv, Behaviours behaviours  )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) -> {

			(new Thread( () -> {
				final int currentSource = bdv.getViewerPanel().getVisibilityAndGrouping().getCurrentSource();
				if ( currentSource == 0 ) return;
				bdv.getViewerPanel().getVisibilityAndGrouping().setCurrentSource( currentSource - 1 );
			} )).start();

		}, "Go to previous source", "J" ) ;

		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) -> {

			(new Thread( () -> {
				final int currentSource = bdv.getViewerPanel().getVisibilityAndGrouping().getCurrentSource();
				if ( currentSource == bdv.getViewerPanel().getVisibilityAndGrouping().numSources() - 1  ) return;
				bdv.getViewerPanel().getVisibilityAndGrouping().setCurrentSource( currentSource + 1 );
			} )).start();

		}, "Go to next source", "K" ) ;
	}
}
