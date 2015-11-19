package amidst.map;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import amidst.Options;
import amidst.map.layer.LiveLayer;
import amidst.map.object.MapObject;
import amidst.minecraft.Biome;
import amidst.minecraft.world.FileWorld.Player;
import amidst.utilities.CoordinateUtils;

public class Map {
	public class Drawer {
		private final Runnable imageLayersDrawer = createImageLayersDrawer();
		private final Runnable liveLayersDrawer = createLiveLayersDrawer();
		private final Runnable objectsDrawer = createObjectsDrawer();

		private AffineTransform mat = new AffineTransform();
		private Fragment currentFragment;
		private Graphics2D g2d;
		private float time;

		private Runnable createImageLayersDrawer() {
			return new Runnable() {
				@Override
				public void run() {
					drawImageLayers(currentFragment, time, g2d, mat);
				}
			};
		}

		public void drawImageLayers(Fragment fragment, float time,
				Graphics2D g2d, AffineTransform mat) {
			if (fragment.isLoaded()) {
				fragment.updateAlpha(time);
				for (int i = 0; i < fragment.getImages().length; i++) {
					if (fragment.getImageLayers()[i].isVisible()) {
						setAlphaComposite(
								g2d,
								fragment.getAlpha()
										* fragment.getImageLayers()[i]
												.getAlpha());

						// TODO: FIX THIS
						g2d.setTransform(fragment.getImageLayers()[i]
								.getScaledMatrix(mat));
						if (g2d.getTransform().getScaleX() < 1.0f) {
							g2d.setRenderingHint(
									RenderingHints.KEY_INTERPOLATION,
									RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						} else {
							g2d.setRenderingHint(
									RenderingHints.KEY_INTERPOLATION,
									RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
						}
						g2d.drawImage(fragment.getImages()[i], 0, 0, null);
					}
				}
				setAlphaComposite(g2d, 1.0f);
			}
		}

		private Runnable createLiveLayersDrawer() {
			return new Runnable() {
				@Override
				public void run() {
					drawLiveLayers(currentFragment, time, g2d, mat);
				}
			};
		}

		public void drawLiveLayers(Fragment fragment, float time,
				Graphics2D g2d, AffineTransform mat) {
			for (LiveLayer liveLayer : fragment.getLiveLayers()) {
				if (liveLayer.isVisible()) {
					liveLayer.drawLive(fragment, g2d, mat);
				}
			}
		}

		private Runnable createObjectsDrawer() {
			return new Runnable() {
				@Override
				public void run() {
					drawObjects(currentFragment, g2d, mat, Map.this);
				}
			};
		}

		public void drawObjects(Fragment fragment, Graphics2D g2d,
				AffineTransform mat, Map map) {
			if (fragment.getAlpha() != 1.0f) {
				setAlphaComposite(g2d, fragment.getAlpha());
			}
			for (MapObject mapObject : fragment.getMapObjects()) {
				drawObject(g2d, mat, mapObject, map);
			}
			if (fragment.getAlpha() != 1.0f) {
				setAlphaComposite(g2d, 1.0f);
			}
		}

		private void drawObject(Graphics2D g2d, AffineTransform mat,
				MapObject mapObject, Map map) {
			if (mapObject.isVisible()) {
				double invZoom = 1.0 / map.getZoom();
				int width = mapObject.getWidth();
				int height = mapObject.getHeight();
				if (map.getSelectedMapObject() == mapObject) {
					width *= 1.5;
					height *= 1.5;
				}
				g2d.setTransform(mat);
				g2d.translate(mapObject.getXInFragment(),
						mapObject.getYInFragment());
				g2d.scale(invZoom, invZoom);
				g2d.drawImage(mapObject.getImage(), -(width >> 1),
						-(height >> 1), width, height, null);
			}
		}

		private void setAlphaComposite(Graphics2D g2d, float alpha) {
			g2d.setComposite(AlphaComposite.getInstance(
					AlphaComposite.SRC_OVER, alpha));
		}

		public void draw(Graphics2D g2d, float time) {
			this.g2d = g2d;
			this.time = time;
			AffineTransform originalTransform = g2d.getTransform();
			drawLayer(originalTransform, imageLayersDrawer);
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			layerContainer.updateAllLayers(time);
			drawLayer(originalTransform, liveLayersDrawer);
			drawLayer(originalTransform, objectsDrawer);
			g2d.setTransform(originalTransform);
		}

		private void drawLayer(AffineTransform originalTransform,
				Runnable theDrawer) {
			if (startFragment != null) {
				initMat(originalTransform, zoom.getCurrentValue());
				for (Fragment fragment : startFragment) {
					currentFragment = fragment;
					theDrawer.run();
					mat.translate(Fragment.SIZE, 0);
					if (currentFragment.isEndOfLine()) {
						mat.translate(-Fragment.SIZE * fragmentsPerRow,
								Fragment.SIZE);
					}
				}
			}
		}

		private void initMat(AffineTransform originalTransform, double scale) {
			mat.setToIdentity();
			mat.concatenate(originalTransform);
			mat.translate(startOnScreen.x, startOnScreen.y);
			mat.scale(scale, scale);
		}
	}

	private Drawer drawer = new Drawer();

	private MapObject selectedMapObject;

	private Fragment startFragment;

	private Point2D.Double startOnScreen = new Point2D.Double();

	private int fragmentsPerRow;
	private int fragmentsPerColumn;
	private int viewerWidth = 1;
	private int viewerHeight = 1;

	private final Object mapLock = new Object();

	private FragmentManager fragmentManager;
	private MapZoom zoom;
	private LayerContainer layerContainer;

	public Map(FragmentManager fragmentManager, MapZoom zoom,
			LayerContainer layerContainer) {
		this.fragmentManager = fragmentManager;
		this.zoom = zoom;
		this.layerContainer = layerContainer;
		this.layerContainer.reloadAllLayers(this);
		this.fragmentManager.start();
		safeAddStart(0, 0);
	}

	private void lockedDraw(Graphics2D g2d, float time) {
		int fragmentSizeOnScreen = zoom.worldToScreen(Fragment.SIZE);
		int desiredFragmentsPerRow = viewerWidth / fragmentSizeOnScreen + 2;
		int desiredFragmentsPerColumn = viewerHeight / fragmentSizeOnScreen + 2;
		lockedAdjustNumberOfRowsAndColumns(fragmentSizeOnScreen,
				desiredFragmentsPerRow, desiredFragmentsPerColumn);
		drawer.draw(g2d, time);
	}

	private void lockedAdjustNumberOfRowsAndColumns(int fragmentSizeOnScreen,
			int desiredFragmentsPerRow, int desiredFragmentsPerColumn) {
		int newColumns = desiredFragmentsPerRow - fragmentsPerRow;
		int newRows = desiredFragmentsPerColumn - fragmentsPerColumn;
		int newLeft = 0;
		int newAbove = 0;
		while (startOnScreen.x > 0) {
			startOnScreen.x -= fragmentSizeOnScreen;
			newLeft++;
		}
		while (startOnScreen.x < -fragmentSizeOnScreen) {
			startOnScreen.x += fragmentSizeOnScreen;
			newLeft--;
		}
		while (startOnScreen.y > 0) {
			startOnScreen.y -= fragmentSizeOnScreen;
			newAbove++;
		}
		while (startOnScreen.y < -fragmentSizeOnScreen) {
			startOnScreen.y += fragmentSizeOnScreen;
			newAbove--;
		}
		int newRight = newColumns - newLeft;
		int newBelow = newRows - newAbove;
		startFragment = startFragment.adjustRowsAndColumns(newAbove, newBelow,
				newLeft, newRight, fragmentManager);
		fragmentsPerRow = fragmentsPerRow + newLeft + newRight;
		fragmentsPerColumn = fragmentsPerColumn + newAbove + newBelow;
	}

	private void lockedAddStart(int x, int y) {
		startFragment = fragmentManager.requestFragment(x, y);
		fragmentsPerRow = 1;
		fragmentsPerColumn = 1;
	}

	// TODO: Support longs?
	private void lockedCenterOn(long xInWorld, long yInWorld) {
		if (startFragment != null) {
			startFragment = startFragment.recycleAll(fragmentManager);
		}
		int xCenterOnScreen = viewerWidth >> 1;
		int yCenterOnScreen = viewerHeight >> 1;
		long xFragmentRelative = CoordinateUtils.toFragmentRelative(xInWorld);
		long yFragmentRelative = CoordinateUtils.toFragmentRelative(yInWorld);
		startOnScreen.x = xCenterOnScreen
				- zoom.worldToScreen(xFragmentRelative);
		startOnScreen.y = yCenterOnScreen
				- zoom.worldToScreen(yFragmentRelative);
		long xFragmentCorner = CoordinateUtils.toFragmentCorner(xInWorld);
		long yFragmentCorner = CoordinateUtils.toFragmentCorner(yInWorld);
		lockedAddStart((int) xFragmentCorner, (int) yFragmentCorner);
	}

	private void safeAddStart(int startX, int startY) {
		synchronized (mapLock) {
			lockedAddStart(startX, startY);
		}
	}

	public void safeDraw(Graphics2D g2d, float time) {
		synchronized (mapLock) {
			lockedDraw(g2d, time);
		}
	}

	public void safeCenterOn(long x, long y) {
		synchronized (mapLock) {
			lockedCenterOn(x, y);
		}
	}

	public void safeDispose() {
		synchronized (mapLock) {
			lockedDispose();
		}
	}

	public Fragment getFragmentAt(Point position) {
		Point cornerPosition = new Point(position.x >> Fragment.SIZE_SHIFT,
				position.y >> Fragment.SIZE_SHIFT);
		Point fragmentPosition = new Point();
		if (startFragment != null) {
			for (Fragment fragment : startFragment) {
				fragmentPosition.x = fragment.getFragmentXInWorld();
				fragmentPosition.y = fragment.getFragmentYInWorld();
				if (cornerPosition.equals(fragmentPosition)) {
					return fragment;
				}
			}
		}
		return null;
	}

	public MapObject getMapObjectAt(Point positionOnScreen, double maxRange) {
		double x = startOnScreen.x;
		double y = startOnScreen.y;
		MapObject closestObject = null;
		double closestDistance = maxRange;
		int fragmentSizeOnScreen = zoom.worldToScreen(Fragment.SIZE);
		if (startFragment != null) {
			for (Fragment fragment : startFragment) {
				for (MapObject mapObject : fragment.getMapObjects()) {
					if (mapObject.isVisible()) {
						double distance = getPositionOnScreen(x, y, mapObject)
								.distance(positionOnScreen);
						if (closestDistance > distance) {
							closestDistance = distance;
							closestObject = mapObject;
						}
					}
				}
				x += fragmentSizeOnScreen;
				if (fragment.isEndOfLine()) {
					x = startOnScreen.x;
					y += fragmentSizeOnScreen;
				}
			}
		}
		return closestObject;
	}

	private Point getPositionOnScreen(double x, double y, MapObject mapObject) {
		Point result = new Point(mapObject.getXInFragment(),
				mapObject.getYInFragment());
		result.x = zoom.worldToScreen(result.x);
		result.y = zoom.worldToScreen(result.y);
		result.x += x;
		result.y += y;
		return result;
	}

	public String getBiomeNameAt(Point point) {
		if (startFragment != null) {
			for (Fragment fragment : startFragment) {
				if ((fragment.getXInWorld() <= point.x)
						&& (fragment.getYInWorld() <= point.y)
						&& (fragment.getXInWorld() + Fragment.SIZE > point.x)
						&& (fragment.getYInWorld() + Fragment.SIZE > point.y)) {
					int x = point.x - fragment.getXInWorld();
					int y = point.y - fragment.getYInWorld();

					return getBiomeNameForFragment(fragment, x, y);
				}
			}
		}
		return "Unknown";
	}

	public String getBiomeAliasAt(Point point) {
		if (startFragment != null) {
			for (Fragment fragment : startFragment) {
				if ((fragment.getXInWorld() <= point.x)
						&& (fragment.getYInWorld() <= point.y)
						&& (fragment.getXInWorld() + Fragment.SIZE > point.x)
						&& (fragment.getYInWorld() + Fragment.SIZE > point.y)) {
					int x = point.x - fragment.getXInWorld();
					int y = point.y - fragment.getYInWorld();

					return getBiomeAliasForFragment(fragment, x, y);
				}
			}
		}
		return "Unknown";
	}

	private String getBiomeNameForFragment(Fragment fragment, int blockX,
			int blockY) {
		return Biome.biomes[getBiomeForFragment(fragment, blockX, blockY)].name;
	}

	private String getBiomeAliasForFragment(Fragment fragment, int blockX,
			int blockY) {
		return Options.instance.biomeColorProfile
				.getAliasForId(getBiomeForFragment(fragment, blockX, blockY));
	}

	private int getBiomeForFragment(Fragment fragment, int blockX, int blockY) {
		int index = (blockY >> 2) * Fragment.BIOME_SIZE + (blockX >> 2);
		return fragment.getBiomeData()[index];
	}

	public void moveBy(Point2D.Double speed) {
		moveBy(speed.x, speed.y);
	}

	public void moveBy(double x, double y) {
		startOnScreen.x += x;
		startOnScreen.y += y;
	}

	public Point screenToWorld(Point pointOnScreen) {
		Point result = pointOnScreen.getLocation();

		result.x -= startOnScreen.x;
		result.y -= startOnScreen.y;

		result.x = zoom.screenToWorld(result.x);
		result.y = zoom.screenToWorld(result.y);

		result.x += startFragment.getXInWorld();
		result.y += startFragment.getYInWorld();

		return result;
	}

	public Point2D.Double getScaled(double oldScale, double newScale, Point p) {
		double baseX = p.x - startOnScreen.x;
		double scaledX = baseX - (baseX / oldScale) * newScale;

		double baseY = p.y - startOnScreen.y;
		double scaledY = baseY - (baseY / oldScale) * newScale;

		return new Point2D.Double(scaledX, scaledY);
	}

	private void repaintImageLayer(int id) {
		if (startFragment != null) {
			for (Fragment fragment : startFragment) {
				fragmentManager.repaintFragmentImageLayer(fragment, id);
			}
		}
	}

	private void lockedDispose() {
		fragmentManager.reset();
	}

	public double getZoom() {
		return zoom.getCurrentValue();
	}

	public int getFragmentsPerRow() {
		return fragmentsPerRow;
	}

	public int getFragmentsPerColumn() {
		return fragmentsPerColumn;
	}

	public void setViewerWidth(int viewerWidth) {
		this.viewerWidth = viewerWidth;
	}

	public void setViewerHeight(int viewerHeight) {
		this.viewerHeight = viewerHeight;
	}

	public MapObject getSelectedMapObject() {
		return selectedMapObject;
	}

	public void setSelectedMapObject(MapObject selectedMapObject) {
		this.selectedMapObject = selectedMapObject;
	}

	public FragmentManager getFragmentManager() {
		return fragmentManager;
	}

	// TODO: move the thread somewhere else?
	@Deprecated
	public void repaintBiomeLayer() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				repaintImageLayer(layerContainer.getBiomeLayer().getLayerId());
			}
		}).start();
	}

	@Deprecated
	public void updatePlayerPosition(Player player, Point newLocationOnScreen) {
		Point locationInWorld = screenToWorld(newLocationOnScreen);
		Fragment newFragment = getFragmentAt(locationInWorld);
		player.moveTo(locationInWorld.x, locationInWorld.y);
		layerContainer.getPlayerLayer().updatePlayerPosition(player,
				newFragment);
	}
}
