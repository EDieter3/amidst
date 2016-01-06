package amidst.gui.main.viewer;

import java.awt.Component;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;

import amidst.documentation.AmidstThread;
import amidst.documentation.CalledOnlyBy;
import amidst.documentation.NotThreadSafe;
import amidst.fragment.FragmentGraph;
import amidst.fragment.layer.LayerReloader;
import amidst.mojangapi.world.World;
import amidst.mojangapi.world.WorldSeed;
import amidst.mojangapi.world.coordinates.CoordinatesInWorld;
import amidst.mojangapi.world.icon.WorldIcon;
import amidst.mojangapi.world.player.MovablePlayerList;
import amidst.threading.WorkerExecutor;

/**
 * This class works as wrapper around a world instance. It holds everything that
 * is needed to display the world on the screen. This allows us to easily
 * exchange the currently displayed world.
 */
@NotThreadSafe
public class ViewerFacade {
	private final World world;
	private final FragmentGraph graph;
	private final FragmentGraphToScreenTranslator translator;
	private final Zoom zoom;
	private final Viewer viewer;
	private final LayerReloader layerReloader;
	private final WorldIconSelection worldIconSelection;
	private final Runnable onRepainterTick;
	private final Runnable onFragmentLoaderTick;
	private final Runnable onPlayerFinishedLoading;

	@CalledOnlyBy(AmidstThread.EDT)
	public ViewerFacade(World world, FragmentGraph graph,
			FragmentGraphToScreenTranslator translator, Zoom zoom,
			Viewer viewer, LayerReloader layerReloader,
			WorldIconSelection worldIconSelection, Runnable onRepainterTick,
			Runnable onFragmentLoaderTick, Runnable onPlayerFinishedLoading) {
		this.world = world;
		this.graph = graph;
		this.translator = translator;
		this.zoom = zoom;
		this.viewer = viewer;
		this.layerReloader = layerReloader;
		this.worldIconSelection = worldIconSelection;
		this.onRepainterTick = onRepainterTick;
		this.onFragmentLoaderTick = onFragmentLoaderTick;
		this.onPlayerFinishedLoading = onPlayerFinishedLoading;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public Component getComponent() {
		return viewer.getComponent();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void reloadBiomeLayer() {
		layerReloader.reloadBiomeLayer();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void reloadPlayerLayer() {
		layerReloader.reloadPlayerLayer();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void dispose() {
		graph.dispose();
		zoom.skipFading();
		zoom.reset();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public Runnable getOnRepainterTick() {
		return onRepainterTick;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public Runnable getOnFragmentLoaderTick() {
		return onFragmentLoaderTick;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void centerOn(CoordinatesInWorld coordinates) {
		translator.centerOn(coordinates);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void centerOn(WorldIcon worldIcon) {
		translator.centerOn(worldIcon.getCoordinates());
		worldIconSelection.select(worldIcon);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public BufferedImage createCaptureImage() {
		return viewer.createCaptureImage();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void adjustZoom(int notches) {
		zoom.adjustZoom(viewer.getMousePositionOrCenter(), notches);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void adjustZoom(Point mousePosition, int notches) {
		zoom.adjustZoom(mousePosition, notches);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void selectWorldIcon(WorldIcon worldIcon) {
		worldIconSelection.select(worldIcon);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public WorldSeed getWorldSeed() {
		return world.getWorldSeed();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public WorldIcon getSpawnWorldIcon() {
		return world.getSpawnWorldIcon();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public List<WorldIcon> getStrongholdWorldIcons() {
		return world.getStrongholdWorldIcons();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public List<WorldIcon> getPlayerWorldIcons() {
		return world.getPlayerWorldIcons();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public MovablePlayerList getMovablePlayerList() {
		return world.getMovablePlayerList();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public boolean canLoadPlayerLocations() {
		return world.getMovablePlayerList().canLoad();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void loadPlayers(WorkerExecutor workerExecutor) {
		worldIconSelection.clear();
		world.getMovablePlayerList().load(workerExecutor,
				onPlayerFinishedLoading);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public boolean canSavePlayerLocations() {
		return world.getMovablePlayerList().canSave();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public void savePlayerLocations() {
		world.getMovablePlayerList().save();
	}
}