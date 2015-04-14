package arkhados.net;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.network.HostedConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;


public abstract class AbstractSender  extends AbstractAppState
        implements Sender {

    protected static final Logger logger =
            Logger.getLogger(AbstractSender.class.getName());

    static {
        logger.setLevel(Level.INFO);
    }
    private int otmIdCounter = 0;
    private boolean shouldSend;
    private Application app;

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        this.app = app;
    }

    protected OneTrueMessage createOneTrueMessage(HostedConnection connection) {
        List<OtmIdCommandListPair> unconfirmedGuaranteed =
                getGuaranteedForSource(connection);
        List<Command> enqueuedGuaranteed =
                getEnqueuedGuaranteedForSource(connection);
        List<Command> enqueuedUnreliables =
                getEnqueuedUnreliablesForSource(connection);

        OneTrueMessage otm = new OneTrueMessage(otmIdCounter);

        if (!enqueuedGuaranteed.isEmpty()) {
            unconfirmedGuaranteed.add(new OtmIdCommandListPair(otmIdCounter,
                    new ArrayList<>(enqueuedGuaranteed)));
        }

        if (!unconfirmedGuaranteed.isEmpty()) {
            otm.getGuaranteed().addAll(unconfirmedGuaranteed);
        }
        if (!enqueuedUnreliables.isEmpty()) {
            otm.getUnreliables().addAll(enqueuedUnreliables);
        }

        enqueuedGuaranteed.clear();
        enqueuedUnreliables.clear();

        return otm;
    }

    private void confirmAllUntil(Object source, int until) {
        List<OtmIdCommandListPair> listToRemoveFrom =
                getGuaranteedForSource(source);

        for (Iterator<OtmIdCommandListPair> it = listToRemoveFrom.iterator();
                it.hasNext();) {
            OtmIdCommandListPair otmIdCommandListPair = it.next();
            if (otmIdCommandListPair.getOtmId() <= until) {
                it.remove();
            } else if (otmIdCommandListPair.getOtmId() > until) {
                break;
            }
        }
    }

    public abstract void addCommand(Command command);

    @Override
    public void update(float tpf) {
        super.update(tpf);

        if (!shouldSend) {
            return;
        }

        sendMessage();

        ++otmIdCounter;

        shouldSend = false;
    }

    public abstract void sendMessage();

    @Override
    public void readGuaranteed(Object source, Command guaranteed) {
    }

    @Override
    public void readUnreliable(final Object source, final Command unreliable) {
        if (unreliable instanceof Ack) {
            app.enqueue(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Ack ack = (Ack) unreliable;
                    confirmAllUntil(source, ack.getConfirmedOtmId());
                    return null;
                }
            });
        }
    }

    public abstract boolean isClient();

    public abstract boolean isServer();

    public abstract void reset();

    protected abstract List<OtmIdCommandListPair> getGuaranteedForSource(
            Object source);

    protected abstract List<Command> getEnqueuedGuaranteedForSource(
            HostedConnection connection);

    protected abstract List<Command> getEnqueuedUnreliablesForSource(
            HostedConnection connection);

    public void setShouldSend(boolean shouldSend) {
        this.shouldSend = shouldSend;
    }
}
