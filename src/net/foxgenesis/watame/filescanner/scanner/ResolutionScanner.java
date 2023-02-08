package net.foxgenesis.watame.filescanner.scanner;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.internal.utils.IOUtil;
import net.foxgenesis.watame.filescanner.FileScannerPlugin;
import net.foxgenesis.watame.filescanner.scanner.AttachmentScanner.AttachmentException;



/**
 * 
 * @author Spaz-Master
 *
 */
public class ResolutionScanner implements AttachmentScanner {
	
	
	private static final Logger logger = LoggerFactory.getLogger("ResolutionScanner");
	private static final int TIMEOUT_VALUE = 2;
	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;

	@Override
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment)
			throws AttachmentException {
		// TODO Auto-generated method stub
		
		if(! attachment.isVideo())
			return null;
		
		
		Process p;
		try {
			ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "frame=pkt_pts_time,width,height", "-select_streams", 
	                "v", "-of", "csv=p=0", "-i", "-");
			pb.redirectErrorStream(true);
			p = pb.start();
		
		}catch(IOException e) {
			logger.error("Error while starting ffprobe", e);
			return CompletableFuture.failedFuture(e);
		}
		
		CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
			try {
				return new String(p.getErrorStream().readAllBytes());
			} catch (IOException e) {
				// unfortunately, if interupted then all our data is lost
				logger.error("Reading from ffmpeg was interrupted: ", e);
				throw new CompletionException(e);
			} finally {
				IOUtil.silentClose(p.getErrorStream());
			}
		}, FileScannerPlugin.SCANNING_POOL).orTimeout(TIMEOUT_VALUE, TIMEOUT_UNIT);
		
		String output;
		try {
			output = cf.get();
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			logger.error("Failed to obtain ffprobe processed data", e);
			return CompletableFuture.failedFuture(e);
		}
		
		//the entire raw ffprobe string data has been obtained.
		//begin splitting it up 
		String[] numbers = output.split("\n");
		//each line consists of the x coord, a comma, and a y coord per each frame.
		//split them up by lines
		String[] resString = numbers[0].split(",");
		//get the x coord and they y coord separately
        for(int i = 1; i < numbers.length; i++){
            //while the frame picked up has an invalid timestamp, keep trying to 
            //get the resolution. sometimes the first few frames are invalid
            if(!resString[0].equals("N/A"))
                break;
            resString = numbers[i].split(",");
        }
        if(resString.length != 3){
        	//if the current line doesnt match out x,y specifications, error
            logger.error("Unable to get initial resolution from piped input");
            return CompletableFuture.failedFuture(null);
        }
        
        int h, w;
        
        try{
            w = Integer.parseInt(resString[1]);
            h = Integer.parseInt(resString[2]);
            //get initial literal int values of x and y of the first frame
        }catch(NumberFormatException ex){
            logger.error("Unable to get initial resolution from piped input");
            return CompletableFuture.failedFuture(null);
        }
        
        
        
        
        for(int i = 1; i < numbers.length; i++){
            
            if(numbers[i].equals(""))
            	//if the lines are blank (sometimes that also happens) just keep skipping forwards
                continue;
            
            String[] clump = numbers[i].split(",");
            //get strings of x and y
            
            if(clump.length != 3){
            	//if not of out specifications, error
                logger.error("Failed to obtain testing frame resolution: {}", numbers[i]);
                return CompletableFuture.failedFuture(null);
            }
            
            int tmpH, tmpW;
            
            try{
                tmpW = Integer.parseInt(clump[1]);
                tmpH = Integer.parseInt(clump[2]);
                //get current processed frame literal
            
            }catch(NumberFormatException ex){
            	logger.error("Failed to obtain testing frame resolution: {} by {}", clump[1], clump[2]);
                return CompletableFuture.failedFuture(null);
            }
            
            if(h != tmpH || w != tmpW){
            	//detected change in resolution
                logger.info("Resolution went from {},{} to {},{}",w, h, tmpW, tmpH);
                //p.destroy();
                //???????????
                throw new CompletionException(new AttachmentException(FileScannerPlugin.CRASHER_VIDEO));
            
            }
            

        }
			
		return null;
	}

}
