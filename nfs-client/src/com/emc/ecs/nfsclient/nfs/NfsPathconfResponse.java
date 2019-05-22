/**
 * Copyright 2016 EMC Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.emc.ecs.nfsclient.nfs;

import com.emc.ecs.nfsclient.rpc.RpcException;
import com.emc.ecs.nfsclient.rpc.Xdr;

/**
 * The response, as specified by RFC 1813 (https://tools.ietf.org/html/rfc1813).
 * 
 * <p>
 * Procedure PATHCONF retrieves the pathconf information for a file or
 * directory. If the FSF_HOMOGENEOUS bit is set in FSFINFO3resok.properties, the
 * pathconf information will be the same for all files and directories in the
 * exported file system in which this file or directory resides.
 * </p>
 * 
 * @author seibed
 */
public class NfsPathconfResponse extends NfsResponseBase {

    /**
     * The posix attributes.
     */
    private NfsPosixAttributes _attributes;

    /**
     * Creates the response, as specified by RFC 1813
     * (https://tools.ietf.org/html/rfc1813).
     * 
     * <p>
     * Procedure PATHCONF retrieves the pathconf information for a file or
     * directory. If the FSF_HOMOGENEOUS bit is set in FSFINFO3resok.properties,
     * the pathconf information will be the same for all files and directories
     * in the exported file system in which this file or directory resides.
     * </p>
     * 
     * @param nfsVersion
     *            The NFS version number. This is ignored for now, as only NFSv3
     *            is supported, but is included to allow future support for
     *            other versions.
     */
    public NfsPathconfResponse(int nfsVersion) {
        super();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.ecs.nfsclient.nfs.NfsResponseBase#unmarshalling(com.emc.ecs.
     * nfsclient.rpc.Xdr)
     */
    public void unmarshalling(Xdr xdr) throws RpcException {
        super.unmarshalling(xdr);
        unmarshallingAttributes(xdr);
        if (stateIsOk()) {
            _attributes = new NfsPosixAttributes();
            _attributes.unmarshalling(xdr);
        }
    }

    /**
     * @return The posix attributes.
     */
    public NfsPosixAttributes getPosixAttributes() {
        return _attributes;
    }

}
