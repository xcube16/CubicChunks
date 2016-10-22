package cubicchunks.server.experimental;

import cubicchunks.world.cube.Cube;

@FunctionalInterface
public interface ICubeRequest {

	void accept(Cube cube);

	default float getPriroity() {
		return 0.0f;
	}
}
