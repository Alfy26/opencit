/**
 * 
 */
package com.intel.mountwilson.Service;

import java.util.List;

import com.intel.mountwilson.common.WLMPortalException;
import com.intel.mountwilson.datamodel.OSDataVO;
import com.intel.mtwilson.WhitelistService;

/**
 * @author yuvrajsx
 *
 */
public interface IOSClientService {
	
	public List<OSDataVO> getAllOS(WhitelistService apiClientServices) throws WLMPortalException;
	public boolean addOSInfo(OSDataVO dataVO, WhitelistService apiClientServices) throws WLMPortalException;
	public boolean updateOSInfo(OSDataVO dataVO, WhitelistService apiClientServices) throws WLMPortalException;
	public boolean deleteOS(OSDataVO dataVO, WhitelistService apiClientServices) throws WLMPortalException;

}
