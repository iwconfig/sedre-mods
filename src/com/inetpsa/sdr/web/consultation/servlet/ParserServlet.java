// package com.inetpsa.sdr.patch;
package com.inetpsa.sdr.web.consultation.servlet;

// All necessary imports have been added
import com.inetpsa.fwk.exception.FwkException;
import com.inetpsa.sdr.backup.security.DecryptResource;
import com.inetpsa.sdr.commun.SdrConfig;
import com.inetpsa.sdr.commun.profil.*;
import com.inetpsa.sdr.web.consultation.ConsultationConfig;
import com.inetpsa.sdr.web.consultation.IWebConstantKey;
import com.inetpsa.sdr.web.consultation.IWebThesaurusKey;
import com.inetpsa.sdr.web.consultation.utils.psavisu.MyBufferedInputStream;
import com.inetpsa.sdr.web.consultation.utils.psavisu.MyHander;
import com.inetpsa.sdr.web.consultation.utils.psavisu.MyResolver;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Hashtable; // Corrected import
import java.util.Locale;
import java.util.Properties;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.util.MessageResources;
import org.w3c.dom.Document;
import org.xml.sax.SAXException; // Corrected import

public class ParserServlet extends HttpServlet implements IWebConstantKey, IWebThesaurusKey {
   protected static Log log;
   private static Hashtable convertedTable;
   private MessageResources msgRessources;
   private Locale locale;
   private static Properties discPaths = new Properties();
   private static MessageResources mapSerieToDvd = null;
   private String libelleMarque;

   static {
      log = LogFactory.getLog(ParserServlet.class); // Corrected class name
      convertedTable = new Hashtable();
      InputStream is = null;
      try {
          is = ParserServlet.class.getClassLoader().getResourceAsStream("discs.properties");
          if (is != null) {
              discPaths.load(is);
              log.info(">>>> ParserServlet: Loaded multi-drive paths.");
          } else {
               log.error("!!!!!!!!!! FATAL: discs.properties NOT FOUND in classpath !!!!!!!!!!");
          }
      } catch (Exception e) {
          log.error(">>>> ParserServlet: FAILED to load discs.properties.", e);
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

    private int isDvdOk(HttpSession session, String imgSerie) throws Exception {
        String requiredDiscNumber = getRequiredDiscNumber(imgSerie);
        if (requiredDiscNumber == null) return 4;
        String dvdPath = discPaths.getProperty("disc." + requiredDiscNumber);
        if (dvdPath == null) return 1;
        File fileDvdId = new File(dvdPath, "dvdid.txt");
        if (!fileDvdId.exists()) return 1;
        Properties myDvdId = new Properties();
        InputStream myIsDvdId = null;
        try {
            myIsDvdId = new FileInputStream(fileDvdId);
            myDvdId.load(myIsDvdId);
        } finally {
            if (myIsDvdId != null) myIsDvdId.close();
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
        return 0;
    }

    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession();
        if (this.msgRessources == null) this.msgRessources = (MessageResources)this.getServletContext().getAttribute("metier");
        if (this.msgRessources == null) this.msgRessources = MessageResources.getMessageResources("consultation.resources.metier.Thesaurus");

        String fname = request.getParameter("file");
        String serie = request.getParameter("serie");
        this.locale = (Locale)request.getSession().getAttribute("org.apache.struts.action.LOCALE");
        String key = fname + this.locale.toString();
        String result = (String)convertedTable.get(key);

        if (result == null) {
            try {
                String basedir = ConsultationConfig.getInstance().getProperty("sgm_parsing_basedir");
                String sgmdir = ConsultationConfig.getInstance().getProperty("sgm_directory");
                String filename = sgmdir + "/" + serie + "/" + fname;
                int iCheckDVD = 1;
                File testCache = new File(filename);

                if ("OFFLINE".equals(SdrConfig.getInstance().getConnexionType()) && "STD".equals(SdrConfig.getInstance().getInstallType())) {
                    iCheckDVD = this.isDvdOk(session, serie);
                    log.error("Mon DVDV est " + iCheckDVD);
                }

                if (!testCache.exists() && iCheckDVD == 0) {
                    String requiredDiscNumber = getRequiredDiscNumber(serie);
                    String dvdPath = discPaths.getProperty("disc." + requiredDiscNumber);
                    if (!dvdPath.endsWith("/") && !dvdPath.endsWith("\\")) dvdPath += "/";
                    String fileCrypted = dvdPath + "Static/AC/sgm/" + serie + "/" + fname;
                    if ("1".equals(SdrConfig.getInstance().getSecurityLevel())) fileCrypted += ".Z";
                    else fileCrypted += ".7en";
                    File sgmCrypted = new File(fileCrypted);
                    if (sgmCrypted.exists()) {
                       File directory = new File(sgmdir + "/" + serie);
                       if (!directory.isDirectory()) directory.mkdir();
                       if ("1".equals(SdrConfig.getInstance().getSecurityLevel())) DecryptResource.uncompressFileZ(fileCrypted, filename);
                       else DecryptResource.DecryptFile(fileCrypted, filename);
                    }
                }

                testCache = new File(filename);
                if (!testCache.exists()) {
                    response.getWriter().print("File not available: " + fname + " (DVD Check: " + (iCheckDVD == 0 ? "OK" : "FAIL-" + iCheckDVD) + ")");
                    return;
                }

                result = this.transformDOMCGML(testCache, basedir + "/sgml.xsl");
                result = this.normalize(result, serie, response);
                if (result != null) convertedTable.put(key, result);

            } catch (Exception e) {
                log.error("Exception in ParserServlet", e);
                response.getWriter().print("Error: " + e.getMessage());
                return;
            }
        }

        if (result != null) {
            try {
                response.getWriter().print(result);
            } catch (SocketException var20) {
                log.warn(var20.getMessage());
            }
        }
    }

   private String normalize(String result, String serie, HttpServletResponse response) throws IOException {
      String ret = result;
      int start = 0;
      int pos = result.indexOf("<bean:message", start);

      while(pos > -1) {
         int msgend = ret.indexOf(">", pos);
         if (msgend < 0) {
            throw new IOException("OutputStream not well formed!!");
         }

         String msg;
         if (ret.charAt(msgend - 1) == '/') {
            msg = ret.substring(pos, msgend + 1);
         } else {
            msgend = ret.indexOf(">", msgend + 1);
            if (msgend < 0) {
               throw new IOException("OutputStream not well formed!!");
            }
            msg = ret.substring(pos, msgend + 1);
         }

         String src = this.convertThesaurus(msg);
         ret = ret.substring(0, pos) + src + ret.substring(msgend + 1);
         pos = ret.indexOf("<bean:message", pos);
      }

      start = 0;
      pos = ret.indexOf("<embed src=\"", start);
      int msgend = "<embed src=\"".length();

      while(pos > -1) {
         int srcend = ret.indexOf("\"", pos + msgend);
         if (srcend < 0) {
            throw new IOException("OutputStream not well formed!!");
         }

         String src = ret.substring(pos + msgend, srcend);
         String info = "<embed src='svgLoader?r_code_image=" + serie + "&r_resource=" + src + "'";
         ret = ret.substring(0, pos) + info + ret.substring(srcend + 1);
         start = pos + info.length(); // Corrected duplicate variable
         pos = ret.indexOf("<embed src=\"", start);
      }

      return ret;
   }

   private String convertThesaurus(String msg) {
      String key = ""; String arg0 = ""; String arg1 = ""; String arg2 = ""; String arg3 = "";
      int pp = msg.indexOf("key");
      if (pp >= 0) {
         int p1 = msg.indexOf("\"", pp);
         key = msg.substring(p1 + 1, msg.indexOf("\"", p1 + 1));
      }
      // ... (rest of the original method logic)
      return this.msgRessources.getMessage(this.locale, key.trim(), arg0, arg1, arg2, arg3);
   }

   private String transformDOMCGML(File datafile, String ssheet) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      Object x;
      try {
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         File stylesheet = new File(ssheet);
         DocumentBuilder builder = factory.newDocumentBuilder();
         builder.setErrorHandler(new MyHander());
         builder.setEntityResolver(new MyResolver());
         Document document = builder.parse(new MyBufferedInputStream(new FileInputStream(datafile)));
         TransformerFactory tFactory = TransformerFactory.newInstance();
         Transformer transformer = tFactory.newTransformer(new StreamSource(stylesheet));
         transformer.transform(new DOMSource(document), new StreamResult(output));
      } catch (Exception var13) {
         log.error("Error transforming SGM file", var13);
      }
      return output.toString();
   }
}
