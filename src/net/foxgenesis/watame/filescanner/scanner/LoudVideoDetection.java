package net.foxgenesis.watame.filescanner.scanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.foxgenesis.watame.filescanner.FileScannerPlugin;

/**
 * @author Spaz-Master
 *
 */
public class LoudVideoDetection implements AttachmentScanner {
	
	
	/**
	 * length of EBUR128 tag in ffmpeg
	 */
	private final int EBUR128 = 35;
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("LoudVideoDetection");
	


	
	/**
	 * Called by the Testing subscriber-and-publisher
	 * @author Spaz-Master
	 * @throws CompletionException	- if detected a loud video
	 * @param in 					- the bytes of the attachment
	 * @param msg					- the message object of the discord message
	 * @param attachment			- the attachment object of the message to scan
	 */
	@Override
	public CompletableFuture<Void> testAttachment(byte[] in, Message msg, Attachment attachment) {

		
		return CompletableFuture.supplyAsync( () -> {
			
			if(! attachment.isVideo())
				return null;
			
			ArrayList<Double> segments;
			try {
				segments = getVolumeSegments(in);
			} catch (IOException e) {
				logger.error("Failed to obtain volume segments: ", e);
				return null;
			}
			if(segments == null) {
				logger.error("Failed to process media file: {}", attachment.toString());
				return null;
			}
			
			
			int strikes = 0;
	        ArrayList<Integer> strikeChunks = new ArrayList<>();
	        /*sometimes a video could have a loud peak for less than a second, possibly due to random noise or encoding error.
	        This acts as a sort of "forgiveness meter" so that it takes more than a one-time detection of loud audio
	        */    
	        
	        for(int i = 0; i < segments.size(); i++){
	            
	            if(segments.get(i) == null){
	                logger.warn("Segment was not processed correctly, skipping...");
	                continue;
	            }
	            
	            double value = segments.get(i);
	                  
	            if(value > -2){
	                //if the loudness value is greater than -4.5
	                strikes++;
	            }
	            else if(strikes > 0){
	            	//otherwise, we have gone back to a segment that isnt loud anymore and we can add a group of loud chunks back into a
	            	//strike cache
	                strikeChunks.add(strikes);
	                strikes = 0;
	            }
	            
	            
	            
	        }//end for loop
	        
	        if(strikes > 0){
	        	//if video ended with loud strikes, then add those chunks as well
	            strikeChunks.add(strikes);
	        }
	        
	        
	        int total = segments.size();
	        for (int strikeChunk : strikeChunks) {
	            //if we have a time period of loudness that is bigger than 1/5th of the total video,
	            //then we triggered loudness detection
	            double percent = (double)strikeChunk / (double)total;
	            if(percent >= .2){
	            	logger.debug("Detected loud video");
	                throw new CompletionException(new AttachmentException(FileScannerPlugin.LOUD_VIDEO));
	            }
	            
	        }
			
			return null;
			
		});
		
		
		
		
	}
	
	
    /**
     * extracts the data of the average volume in accordance to EBUR 128 standard of the attachment
     * 
     * @author Spaz-Master
     * @param buffer byte array of the attachment
     * @return ArrayList of Doubles of read volume average values
     * @throws IOException if some kind of processing error occured with FFMPEG
     */
    public ArrayList<Double> getVolumeSegments(byte[] buffer) throws IOException{
        ArrayList<Double> output = new ArrayList<>();
        
        
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-hide_banner", "-nostats", "-i", "-", "-filter_complex", "ebur128", "-f", "null", "-");
        Process p = pb.start();
        
        
        CompletableFuture<String> stdOutReader = CompletableFuture.supplyAsync( () -> {
        	String out;
			try {
				out = new String(p.getErrorStream().readAllBytes());
				
			} catch (IOException e) {
				//unfortunately, if interupted then all our data is lost
				logger.error("Reading from ffmpeg was interrupted: ", e);
				out = null;
			}finally {
				try {
					p.getErrorStream().close();
				} catch (IOException e) {
					logger.error("Failed to close ffmpeg error stream");
				}
			}
			
        	return out;
        });
        
        
        try{
            p.getOutputStream().write(buffer);
        
        }catch(IOException ex){
            if(!ex.getMessage().equals("Broken pipe"))
            	//The Funny™
                throw ex;
        }
        
        p.getOutputStream().flush();
        p.getOutputStream().close();
        
        
        String joinedResult = stdOutReader.join();
        String[] results = joinedResult.split("\n");
        
        logger.debug("results: {}", results == null);
        
        if(results == null)
        	return null;
        else if(results.length < 2)
            return null;
        
        
        
        for (String tmp : results) {
            if(tmp.startsWith("[Parsed_ebur128_0")){
                int start = tmp.indexOf("M:", EBUR128)+2;
                if(start < 2)
                    continue;
                int end = tmp.indexOf("S:", start);
                
                String loudStr = tmp.substring(start, end);
                
                try{
                    double val = Double.parseDouble(loudStr);
                    output.add(val);
                }catch(NumberFormatException ex){
                    logger.warn("Bad double value "+loudStr+ " skipping...");
                    break;
                }
                
            }
            
        }

        return output;
    
    }
    
   
    
	

}
