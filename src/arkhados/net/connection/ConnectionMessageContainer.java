package arkhados.net.connection;

import com.jme3.network.AbstractMessage;
import com.jme3.network.Message;
import com.jme3.network.serializing.Serializable;

@Serializable
public class ConnectionMessageContainer extends AbstractMessage {

    private int orderNum;
    private boolean confirm;
    private Message message;

    public ConnectionMessageContainer() {
    }

    public ConnectionMessageContainer(int orderNum, Message message, boolean confirm) {
        super(false);
        this.orderNum = orderNum;
        this.message = message;
        this.confirm = confirm;
    }

    public int getOrderNum() {
        return orderNum;
    }
    
    public boolean confirms() {
        return confirm;
    }
    
    public Message getMessage() {
        return message;
    }
}
