package com.cookiecraftmods.roadbuilder.client.testmatrix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

final class MatrixClientConnection {
    private MatrixClientConnection() {
    }

    static void connect(Minecraft client, int port) {
        ServerData data = new ServerData(
                "Road Builder matrix", "127.0.0.1:" + port, ServerData.Type.OTHER
        );
        ConnectScreen.startConnecting(
                new TitleScreen(), client, new ServerAddress("127.0.0.1", port), data, false, null
        );
    }
}
