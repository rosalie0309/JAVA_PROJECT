import java.io.IOException;
import java.util.logging.*;

public class Log {
    public static Logger setup(String name, String filePath) {
        Logger logger = Logger.getLogger(name);
        try {
            FileHandler fh = new FileHandler(filePath, true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return logger;
    }
}
