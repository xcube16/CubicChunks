package cubicchunks.server.experimental;

import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.List;

import cubicchunks.util.CubePos;

public class Example {

	private IViewFormula aPlayersView; // the view formula for a single player
	private EntityPlayerMP player;

	public Example(EntityPlayerMP player) {
		this.aPlayersView = new VanillaViewFormula(10, player);
		this.player = player;
	}

	public void playerMoved(){
		IViewFormula newView = aPlayersView.next(player);
		if(newView == null){
			return;
		}

		List<CubePos> dropedCubes = new ArrayList<>();
		List<CubePos> newCubes = new ArrayList<>();

		// find cubes that dont show up on the newView (dropedCubes)
		aPlayersView.computePositions((x, y, z) -> {
			if(!newView.contains(x, y, z)){
				dropedCubes.add(new CubePos(z, y, z));
			}
		});

		// find the cubes that dont show up on aPlayerView (newCubes)
		newView.computePositions((x, y, z) -> {
			if(!aPlayersView.contains(x, y, z)){
				newCubes.add(new CubePos(z, y, z));
			}
		});

		// newCubes and dropedCubes are filled ;)

		aPlayersView = newView;
	}
}
