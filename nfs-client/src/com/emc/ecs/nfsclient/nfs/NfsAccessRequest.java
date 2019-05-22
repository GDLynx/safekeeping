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

import java.io.FileNotFoundException;

import com.emc.ecs.nfsclient.rpc.Credential;
import com.emc.ecs.nfsclient.rpc.Xdr;

/**
 * The request, as specified by RFC 1813 (https://tools.ietf.org/html/rfc1813).
 * 
 * <p>
 * Procedure ACCESS determines the access rights that a user, as identified by
 * the credentials in the request, has with respect to a file system object. The
 * client encodes the set of permissions that are to be checked in a bit mask.
 * The server checks the permissions encoded in the bit mask. A status of
 * NFS3_OK is returned along with a bit mask encoded with the permissions that
 * the client is allowed.
 * </p>
 * 
 * <p>
 * The results of this procedure are necessarily advisory in nature. That is, a
 * return status of NFS3_OK and the appropriate bit set in the bit mask does not
 * imply that such access will be allowed to the file system object in the
 * future, as access rights can be revoked by the server at any time.
 * </p>
 * 
 * @author seibed
 */
public class NfsAccessRequest extends NfsRequestBase {

    /**
     * A bit mask of access permissions to check, as specified below.
     * 
     * <pre>
    ACCESS3_READ    = 0x0001 - Read data from file or read a directory.
    ACCESS3_LOOKUP  = 0x0002 - Look up a name in a directory (no meaning for non-directory objects).
    ACCESS3_MODIFY  = 0x0004 - Rewrite existing file data or modify existing directory entries.
    ACCESS3_EXTEND  = 0x0008 - Write new data or add directory entries.
    ACCESS3_DELETE  = 0x0010 - Delete an existing directory entry.
    ACCESS3_EXECUTE = 0x0020 - Execute file (no meaning for a directory).
     * </pre>
     */
    private final long _accessToCheck;

    /**
     * Creates the request, as specified by RFC 1813
     * (https://tools.ietf.org/html/rfc1813).
     * 
     * <p>
     * Procedure ACCESS determines the access rights that a user, as identified
     * by the credentials in the request, has with respect to a file system
     * object. The client encodes the set of permissions that are to be checked
     * in a bit mask. The server checks the permissions encoded in the bit mask.
     * A status of NFS3_OK is returned along with a bit mask encoded with the
     * permissions that the client is allowed.
     * </p>
     * 
     * <p>
     * The results of this procedure are necessarily advisory in nature. That
     * is, a return status of NFS3_OK and the appropriate bit set in the bit
     * mask does not imply that such access will be allowed to the file system
     * object in the future, as access rights can be revoked by the server at
     * any time.
     * </p>
     * 
     * @param fileHandle
     *            The file handle for the file system object to which access is
     *            to be checked.
     * @param accessToCheck
     *            A bit mask of access permissions to check, as specified below.
     * 
     *            <pre>
    ACCESS3_READ    = 0x0001 - Read data from file or read a directory.
    ACCESS3_LOOKUP  = 0x0002 - Look up a name in a directory (no meaning for non-directory objects).
    ACCESS3_MODIFY  = 0x0004 - Rewrite existing file data or modify existing directory entries.
    ACCESS3_EXTEND  = 0x0008 - Write new data or add directory entries.
    ACCESS3_DELETE  = 0x0010 - Delete an existing directory entry.
    ACCESS3_EXECUTE = 0x0020 - Execute file (no meaning for a directory).
     *            </pre>
     * 
     * @param credential
     *            The credential used for RPC authentication.
     * @param nfsVersion
     *            The NFS version number
     * @throws FileNotFoundException
     */
    public NfsAccessRequest(byte[] fileHandle, long accessToCheck, Credential credential, int nfsVersion)
            throws FileNotFoundException {
        super(Nfs.RPC_PROGRAM, nfsVersion, Nfs.NFSPROC3_ACCESS, credential, fileHandle);
        _accessToCheck = accessToCheck;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.ecs.nfsclient.nfs.NfsRequestBase#marshalling(com.emc.ecs.
     * nfsclient.rpc.Xdr)
     */
    public void marshalling(Xdr xdr) {
        super.marshalling(xdr);
        xdr.putUnsignedInt(_accessToCheck);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return startToString("NfsAccessRequest").append(" accessToCheck:").append(String.valueOf(_accessToCheck))
                .toString();
    }

}
