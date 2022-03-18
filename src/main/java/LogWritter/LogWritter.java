package LogWritter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class LogWritter{

    private Logger logger;
    public LogWritter(Class c){
        logger= LogManager.getLogger(c.getDeclaringClass());
        //locate custom configuration file
        Configurator.initialize(null,"log4j.xml");
    }

    public void writeLog(String msg,String type){
        switch(type){
            case "info":
                logger.info(msg);
                break;
            case "debug":
                logger.debug(msg);
                break;
            case "warn":
                logger.warn(msg);
                break;
            case "error":
                logger.info(msg);
                break;
            case "fatal":
                logger.fatal(msg);
                break;
        }

    }

}