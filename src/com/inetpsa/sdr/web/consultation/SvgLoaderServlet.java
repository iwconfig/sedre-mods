// We use a new package name to avoid any conflicts
// package com.inetpsa.sdr.patch;
package com.inetpsa.sdr.web.consultation;

import com.inetpsa.fwk.exception.FwkException;
import com.inetpsa.sdr.backup.security.DecryptResource;
import com.inetpsa.sdr.commun.SdrConfig;
import com.inetpsa.sdr.commun.util.FileHelper;
import com.inetpsa.sdr.web.consultation.ConsultationConfig;
import com.inetpsa.sdr.commun.profil.*;
import com.inetpsa.sdr.web.consultation.IWebConstantKey;

import java.io.*;
import java.util.Properties;
import java.util.Locale;
import javax.servlet.http.*;
import javax.servlet.ServletException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.util.MessageResources;

// This is a patched version of the original SvgLoaderServlet
public class SvgLoaderServlet extends HttpServlet implements IWebConstantKey {
    protected static Log log;
    private static final Object decryptionLock = new Object();
    private static Properties discPaths = new Properties();
    private static MessageResources mapSerieToDvd = null;
    private String libelleMarque;

    static {
        log = LogFactory.getLog(SvgLoaderServlet.class);
        InputStream is = null;
        try {
            is = SvgLoaderServlet.class.getClassLoader().getResourceAsStream("discs.properties");
            if (is != null) {
                discPaths.load(is);
                log.info(">>>> SvgLoaderServlet: Loaded multi-drive paths.");
            } else {
                 log.error("!!!!!!!!!! FATAL: discs.properties NOT FOUND !!!!!!!!!!");
            }
        } catch (Exception e) {
            log.error(">>>> SvgLoaderServlet: FAILED to load discs.properties.", e);
        } finally {
            if (is != null) try { is.close(); } catch (IOException e) {}
        }
    }

    private String getRequiredDiscNumber(String imgSerie) {
        if (mapSerieToDvd == null) {
            mapSerieToDvd = MessageResources.getMessageResources("mapseriedvd");
        }
        return mapSerieToDvd.getMessage("serie_" + imgSerie);
    }
    
    // OUR PATCHED, MULTI-DRIVE AWARE VERSION of isDvdOk
    private int isDvdOk(HttpSession session, String imgSerie) throws Exception {
        String requiredDiscNumber = getRequiredDiscNumber(imgSerie);
        if (requiredDiscNumber == null) {
            log.error("No disc mapping for serie: " + imgSerie);
            return 4; // NUM_NOT_VALID
        }

        String dvdPath = discPaths.getProperty("disc." + requiredDiscNumber);
        if (dvdPath == null) {
            log.error("Path for disc " + requiredDiscNumber + " not found in discs.properties.");
            return 1; // DVD_NOT_VALID
        }

        File fileDvdId = new File(dvdPath, "dvdid.txt");
        if (!fileDvdId.exists()) {
             log.error("dvdid.txt not found at path: " + fileDvdId.getAbsolutePath());
             return 1;
        }

        Properties myDvdId = new Properties();
        InputStream myIsDvdId = null;
        try {
            myIsDvdId = new FileInputStream(fileDvdId);
            myDvdId.load(myIsDvdId);
        } finally {
            if (myIsDvdId != null) {
                try {
                    myIsDvdId.close();
                } catch (IOException e) {
                    // Ignore close exception
                }
            }
        }
        
        SdrUser user = (SdrUser) session.getAttribute("user");
        ISdrProfil profil = user.getSdrProfil();
        String marque = "";
        if (profil instanceof ProfilOFFCitroen) { marque = "AC"; this.libelleMarque = "Citroen"; }
        if (profil instanceof ProfilOFFPeugeot) { marque = "AP"; this.libelleMarque = "Peugeot"; }

        String infoDvdId = myDvdId.getProperty("dvdId");
        String infoMarque = myDvdId.getProperty("marque");
        String infoDataVersion = myDvdId.getProperty("data.version");
        String dataVersion = SdrConfig.getInstance().getDataVersion();

        if (infoDvdId == null || infoMarque == null || infoDataVersion == null || infoDvdId.length() < 5) return 1;
        if (!"sdr".equalsIgnoreCase(infoDvdId.substring(0, 3))) return 2;
        if (!marque.equalsIgnoreCase(infoMarque)) return 3;
        if (!requiredDiscNumber.equals(infoDvdId.substring(4))) return 4;
        if (!infoDataVersion.equals(dataVersion)) return infoDataVersion.compareTo(dataVersion) < 0 ? 5 : 6;
        
        return 0; // SUCCESS
    }

    private boolean isGzipped(File file) throws IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            return in.read() == 0x1f && in.read() == 0x8b;
        } finally {
            if (in != null) in.close();
        }
    }

    // THIS IS THE ORIGINAL SERVICE METHOD LOGIC, UNTOUCHED
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if(log.isDebugEnabled()) log.debug("+");
        try {
            ConsultationConfig config = ConsultationConfig.getInstance();
            String codeImage = request.getParameter("r_code_image");
            String resource = request.getParameter("r_resource");
            if (codeImage == null) throw new FwkException("r_code_image parameter is mandatory");
            if (resource == null) throw new FwkException("r_resource parameter is mandatory");
            if (!resource.toLowerCase().endsWith(".svgz") && !resource.toLowerCase().endsWith(".jpg") && !resource.toLowerCase().endsWith(".jpeg")) throw new FwkException("this resource sufix is not allowed :" + resource);
            
            String filename = config.getSvgDirectory() + "/" + codeImage + "/" + resource;
            File testCache = new File(filename);

            synchronized(decryptionLock) {
                if(!testCache.exists()){
                    int iCheckDVD = 0;
                    if ("OFFLINE".equals(SdrConfig.getInstance().getConnexionType()) && "STD".equals(SdrConfig.getInstance().getInstallType())) {
                        iCheckDVD = this.isDvdOk(request.getSession(), codeImage);
                    }
                    if(!testCache.exists() && iCheckDVD == 0) {
                        String requiredDiscNumber = getRequiredDiscNumber(codeImage);
                        String dvdPath = discPaths.getProperty("disc." + requiredDiscNumber);
                        if (!dvdPath.endsWith("/") && !dvdPath.endsWith("\\")) dvdPath += "/";

                        String fileCrypted = dvdPath + "Static/AC/svg/" + codeImage + "/" + resource;

                        if ("1".equals(SdrConfig.getInstance().getSecurityLevel())) fileCrypted += ".Z";
                        else fileCrypted += ".7en";

                        File svgCrypted = new File(fileCrypted);
                        if(svgCrypted.exists()){
                            File directory = new File(config.getSvgDirectory() + "/" + codeImage);
                            if(!directory.isDirectory()) directory.mkdir();

                            if ("1".equals(SdrConfig.getInstance().getSecurityLevel())) DecryptResource.uncompressFileZ(fileCrypted, filename);
                            else DecryptResource.DecryptFile(fileCrypted, filename);
                        }
                    }
                }
            }
            
            testCache = new File(filename); // Re-check after potential decryption
            if(testCache.exists()){
                if (resource.toLowerCase().endsWith(".svgz")) {
                    response.setContentType("image/svg+xml");
                    if (isGzipped(testCache)) { // Use our isGzipped check
                        response.setHeader("Content-Encoding", "gzip");
                    }
                } else {
                    response.setContentType("image/jpeg");
                }
                FileHelper helper = new FileHelper(filename);
                helper.readWriteArray(filename, response.getOutputStream());
            } else {
                 response.getWriter().print("File not available: " + resource);
            }
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (Exception e) {
            log.error("Exception in SvgLoaderServlet: ", e);
        }
        if(log.isDebugEnabled()) log.debug("-");
    }
}
