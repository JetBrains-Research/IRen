package org.jetbrains.iren;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InvokeLaterService {
    private final List<Consumer<String>> consumerList = new ArrayList<>();

    public static @NotNull InvokeLaterService getInstance() {
        return ServiceManager.getService(InvokeLaterService.class);
    }

    public void save(Consumer<String> consumer) {
//        TODO: think how to handle lookup canceling
        consumerList.add(consumer);
    }

    public void acceptAll(String name) {
        for (Consumer<String> c : consumerList) {
            c.accept(name);
        }
        consumerList.clear();
    }
}
