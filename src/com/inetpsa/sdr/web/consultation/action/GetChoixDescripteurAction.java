// MUST be in this exact package to override the original
package com.inetpsa.sdr.web.consultation.action;

import com.inetpsa.fwk.exception.FwkException;
import com.inetpsa.fwk.service.BusinessService;
import com.inetpsa.sdr.bean.Descripteurs;
import com.inetpsa.sdr.bean.Series;
import com.inetpsa.sdr.commun.IConstantKey;
import com.inetpsa.sdr.commun.SdrConfig;
import com.inetpsa.sdr.commun.action.AbstractSdrAction;
import com.inetpsa.sdr.commun.profil.ISdrProfil;
import com.inetpsa.sdr.commun.profil.ProfilOFFCitroen;
import com.inetpsa.sdr.commun.profil.ProfilOFFPeugeot;
import com.inetpsa.sdr.commun.profil.SdrUser;
import com.inetpsa.sdr.web.consultation.IWebConstantKey;
import com.inetpsa.sdr.web.consultation.SessionCleaner;
import com.inetpsa.sdr.web.consultation.SessionHelper;
import com.inetpsa.sdr.web.consultation.form.DescripteursChoixForm;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException; // Added for IOException
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;

// This is a complete replacement for the original class
public class GetChoixDescripteurAction extends AbstractHistorisationFonctionAction implements IWebConstantKey, IConstantKey {
    protected final Log log;
    private String libelleMarque; // Needed for our patched method

    // --- Static members needed for our patched method ---
    private static Properties discPaths = new Properties();
    private static MessageResources mapSerieToDvd = null;

    static {
        // Load our multi-disc configuration once
        InputStream is = null;
        try {
            is = GetChoixDescripteurAction.class.getClassLoader().getResourceAsStream("discs.properties");
            if (is != null) {
                discPaths.load(is);
            }
        } catch (Exception e) {
            // Can't use log here yet, it's initialized in the constructor
        } finally {
            if (is != null) try { is.close(); } catch (IOException e) {}
        }
    }

    public GetChoixDescripteurAction() {
        this.log = LogFactory.getLog(GetChoixDescripteurAction.class);
    }

    // *** THIS IS OUR PATCHED, MULTI-DRIVE AWARE getNumDvd METHOD ***
    private void getNumDvd(HttpSession session, String imgSerie, DescripteursChoixForm choixForm) throws Exception {
        if (mapSerieToDvd == null) {
            mapSerieToDvd = MessageResources.getMessageResources("mapseriedvd");
        }
        String requiredDiscNumber = mapSerieToDvd.getMessage("serie_" + imgSerie);
        if (requiredDiscNumber == null) {
            choixForm.setNumDvd("?"); // Disc not mapped
            return;
        }

        String dvdPath = discPaths.getProperty("disc." + requiredDiscNumber);
        if (dvdPath == null) {
            choixForm.setNumDvd(requiredDiscNumber); // Path not configured, prompt for disc
            return;
        }

        File fileDvdId = new File(dvdPath, "dvdid.txt");
        if (!fileDvdId.exists() || !fileDvdId.isFile()) {
            choixForm.setNumDvd(requiredDiscNumber); // dvdid.txt not found, prompt for disc
            return;
        }
        
        Properties myDvdId = new Properties();
        InputStream myIsDvdId = null;
        try {
            myIsDvdId = new FileInputStream(fileDvdId);
            myDvdId.load(myIsDvdId);
        } finally {
            if (myIsDvdId != null) { try { myIsDvdId.close(); } catch (IOException e) {} }
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

        if (infoDvdId != null && infoMarque != null && infoDataVersion != null && infoDvdId.length() >= 5) {
            String application = infoDvdId.substring(0, 3);
            String num = infoDvdId.substring(4);
            if ("sdr".equalsIgnoreCase(application) && requiredDiscNumber.equals(num) && dataVersion.equals(infoDataVersion) && marque.equalsIgnoreCase(infoMarque)) {
                choixForm.setNumDvd(null); // SUCCESS!
                return;
            }
        }
        
        choixForm.setNumDvd(requiredDiscNumber);
    }

    // --- ALL THE REMAINING METHODS ARE COPIED VERBATIM FROM THE ORIGINAL DECOMPILED CLASS ---

    public ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (this.log.isDebugEnabled()) {
			this.log.debug("+");
		}
		HttpSession session = request.getSession(true);
		String idSession = SessionHelper.getId(session);
		SessionCleaner.cleanFonctionsHistory(session);
		this.processServiceDeleteHistoriqueContexteByFonction(request, idSession);
		Integer idMarque = (Integer)session.getAttribute("s_id_marque");
		request.setAttribute("r_page_carac", "r_page_carac");
		if (request.getParameter("r_from_schema") == null) {
			SessionCleaner.cleanDomaine(session);
			SessionCleaner.cleanSchemas(session);
		} else {
			request.setAttribute("r_from_schema", "true");
		}
		SdrUser user = (SdrUser)session.getAttribute("user");
		ISdrProfil profil = user.getSdrProfil();
		DescripteursChoixForm choixForm = (DescripteursChoixForm)form;
		Integer idSerie = ((Series)session.getAttribute("s_serie")).getIdSerie();
		String imgSerie = ((Series)session.getAttribute("s_serie")).getImgSerie();
		if ("OFFLINE".equals(SdrConfig.getInstance().getConnexionType()) && "STD".equals(SdrConfig.getInstance().getInstallType())) {
			this.getNumDvd(session, imgSerie, choixForm);
		} else {
			choixForm.setNumDvd((String)null);
		}
		choixForm.setListeCarrosserie(this.processService(request, new Integer(1), idSerie, idMarque));
		choixForm.setListeDirection(this.processService(request, new Integer(2), idSerie, idMarque));
		choixForm.setListeMoteur(this.processService(request, new Integer(3), idSerie, idMarque));
		choixForm.setListeTransmission(this.processService(request, new Integer(4), idSerie, idMarque));
		choixForm.setListeFreinage(this.processService(request, new Integer(5), idSerie, idMarque));
		choixForm.setListeAbs(this.processService(request, new Integer(6), idSerie, idMarque));
		this.updateForm(session, choixForm);
		ArrayList descVin = (ArrayList)session.getAttribute("s_liste_descripteurs_discriminants_vin");
		ArrayList optionPresentes = (ArrayList)session.getAttribute("s_liste_descripteurs_discriminants");
		ArrayList optionAbsentes = (ArrayList)session.getAttribute("s_liste_descripteurs_discriminants_en_absence");
		ArrayList r_optionAbsentes;
		Iterator iterator;
		Descripteurs optA;
		Iterator iter;
		Descripteurs descV;
		if (optionPresentes != null && optionPresentes.size() != 0) {
			r_optionAbsentes = (ArrayList)optionPresentes.clone();
			if (descVin != null) {
				iterator = optionPresentes.iterator();
				label107:
				while(iterator.hasNext()) {
					optA = (Descripteurs)iterator.next();
					iter = descVin.iterator();
					while(true) {
						do {
							if (!iter.hasNext()) continue label107;
							descV = (Descripteurs)iter.next();
						} while((optA.getIdDescripteur() == null || !optA.getIdDescripteur().equals(descV.getIdDescripteur())) && (optA.getIdDescripteur() == null || !optA.getIdDescripteur().equals(descV.getIdDescMetier())) && (optA.getIdDescMetier() == null || !optA.getIdDescMetier().equals(descV.getIdDescripteur())));
						r_optionAbsentes.remove(optA);
					}
				}
			}
			if (r_optionAbsentes.size() != 0) request.setAttribute("r_options_presentes", r_optionAbsentes);
		}
		if (optionAbsentes != null && optionAbsentes.size() != 0) {
			r_optionAbsentes = (ArrayList)optionAbsentes.clone();
			if (descVin != null) {
				iterator = optionAbsentes.iterator();
				label79:
				while(iterator.hasNext()) {
					optA = (Descripteurs)iterator.next();
					iter = descVin.iterator();
					while(true) {
						do {
							if (!iter.hasNext()) continue label79;
							descV = (Descripteurs)iter.next();
						} while((optA.getIdDescripteur() == null || !optA.getIdDescripteur().equals(descV.getIdDescripteur())) && (optA.getIdDescripteur() == null || !optA.getIdDescripteur().equals(descV.getIdDescMetier())) && (optA.getIdDescMetier() == null || !optA.getIdDescMetier().equals(descV.getIdDescripteur())));
						r_optionAbsentes.remove(optA);
					}
				}
			}
			if (r_optionAbsentes.size() != 0) request.setAttribute("r_options_absentes", r_optionAbsentes);
		}
		String forward = "success";
		if (this.log.isDebugEnabled()) this.log.debug("-:" + forward);
		return mapping.findForward(forward);
	}

	private ArrayList processService(HttpServletRequest request, final Integer idClasse, final Integer idSerie, final Integer idMarque) throws FwkException {
		return (ArrayList)this.executeService(request, "getListeDescripteurs", new IServiceCallback() {
			public void doOnServiceInput(BusinessService service) throws FwkException {
				service.setInput("classe", idClasse);
				service.setInput("serie", idSerie);
				service.setInput("idMarque", idMarque);
			}
			public Object doOnServiceOutput(BusinessService service) throws FwkException {
				return service.getOutput("descripteurs");
			}
		});
	}

	private DescripteursChoixForm updateForm(HttpSession session, DescripteursChoixForm choixForm) {
		if (session.getAttribute("s_desc_carrosserie") != null) choixForm.setIdCarrosserie(((Descripteurs)session.getAttribute("s_desc_carrosserie")).getIdDescripteur());
		else choixForm.setIdCarrosserie(this.setDefaultId(choixForm.getListeCarrosserie()));
		if (session.getAttribute("s_desc_direction") != null) choixForm.setIdDirection(((Descripteurs)session.getAttribute("s_desc_direction")).getIdDescripteur());
		else choixForm.setIdDirection(this.setDefaultId(choixForm.getListeDirection()));
		if (session.getAttribute("s_desc_moteur") != null) choixForm.setIdMoteur(((Descripteurs)session.getAttribute("s_desc_moteur")).getIdDescripteur());
		else choixForm.setIdMoteur(this.setDefaultId(choixForm.getListeMoteur()));
		if (session.getAttribute("s_desc_transmission") != null) choixForm.setIdTransmission(((Descripteurs)session.getAttribute("s_desc_transmission")).getIdDescripteur());
		else choixForm.setIdTransmission(this.setDefaultId(choixForm.getListeTransmission()));
		if (session.getAttribute("s_desc_freinage") != null) choixForm.setIdFreinage(((Descripteurs)session.getAttribute("s_desc_freinage")).getIdDescripteur());
		else choixForm.setIdFreinage(this.setDefaultId(choixForm.getListeFreinage()));
		if (session.getAttribute("s_desc_abs") != null) choixForm.setIdAbs(((Descripteurs)session.getAttribute("s_desc_abs")).getIdDescripteur());
		else choixForm.setIdAbs(this.setDefaultId(choixForm.getListeAbs()));
		return choixForm;
	}

	private Integer setDefaultId(ArrayList al) {
		return al.size() == 1 ? ((Descripteurs)al.get(0)).getIdDescripteur() : new Integer(0);
	}
}
