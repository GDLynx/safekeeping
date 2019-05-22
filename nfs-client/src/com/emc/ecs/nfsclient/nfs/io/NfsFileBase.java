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
package com.emc.ecs.nfsclient.nfs.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.ecs.nfsclient.nfs.Nfs;
import com.emc.ecs.nfsclient.nfs.NfsAccessRequest;
import com.emc.ecs.nfsclient.nfs.NfsAccessResponse;
import com.emc.ecs.nfsclient.nfs.NfsCommitRequest;
import com.emc.ecs.nfsclient.nfs.NfsCommitResponse;
import com.emc.ecs.nfsclient.nfs.NfsCreateMode;
import com.emc.ecs.nfsclient.nfs.NfsCreateRequest;
import com.emc.ecs.nfsclient.nfs.NfsCreateResponse;
import com.emc.ecs.nfsclient.nfs.NfsDirectoryEntry;
import com.emc.ecs.nfsclient.nfs.NfsDirectoryPlusEntry;
import com.emc.ecs.nfsclient.nfs.NfsException;
import com.emc.ecs.nfsclient.nfs.NfsFsInfoRequest;
import com.emc.ecs.nfsclient.nfs.NfsFsInfoResponse;
import com.emc.ecs.nfsclient.nfs.NfsFsStatRequest;
import com.emc.ecs.nfsclient.nfs.NfsFsStatResponse;
import com.emc.ecs.nfsclient.nfs.NfsGetAttrRequest;
import com.emc.ecs.nfsclient.nfs.NfsGetAttrResponse;
import com.emc.ecs.nfsclient.nfs.NfsGetAttributes;
import com.emc.ecs.nfsclient.nfs.NfsLinkRequest;
import com.emc.ecs.nfsclient.nfs.NfsLinkResponse;
import com.emc.ecs.nfsclient.nfs.NfsLookupRequest;
import com.emc.ecs.nfsclient.nfs.NfsLookupResponse;
import com.emc.ecs.nfsclient.nfs.NfsMkdirRequest;
import com.emc.ecs.nfsclient.nfs.NfsMkdirResponse;
import com.emc.ecs.nfsclient.nfs.NfsMknodRequest;
import com.emc.ecs.nfsclient.nfs.NfsMknodResponse;
import com.emc.ecs.nfsclient.nfs.NfsPathconfRequest;
import com.emc.ecs.nfsclient.nfs.NfsPathconfResponse;
import com.emc.ecs.nfsclient.nfs.NfsReadRequest;
import com.emc.ecs.nfsclient.nfs.NfsReadResponse;
import com.emc.ecs.nfsclient.nfs.NfsReaddirRequest;
import com.emc.ecs.nfsclient.nfs.NfsReaddirResponse;
import com.emc.ecs.nfsclient.nfs.NfsReaddirplusRequest;
import com.emc.ecs.nfsclient.nfs.NfsReaddirplusResponse;
import com.emc.ecs.nfsclient.nfs.NfsReadlinkRequest;
import com.emc.ecs.nfsclient.nfs.NfsReadlinkResponse;
import com.emc.ecs.nfsclient.nfs.NfsRemoveRequest;
import com.emc.ecs.nfsclient.nfs.NfsRemoveResponse;
import com.emc.ecs.nfsclient.nfs.NfsRenameRequest;
import com.emc.ecs.nfsclient.nfs.NfsRenameResponse;
import com.emc.ecs.nfsclient.nfs.NfsRmdirRequest;
import com.emc.ecs.nfsclient.nfs.NfsRmdirResponse;
import com.emc.ecs.nfsclient.nfs.NfsSetAttrRequest;
import com.emc.ecs.nfsclient.nfs.NfsSetAttrResponse;
import com.emc.ecs.nfsclient.nfs.NfsSetAttributes;
import com.emc.ecs.nfsclient.nfs.NfsStatus;
import com.emc.ecs.nfsclient.nfs.NfsSymlinkRequest;
import com.emc.ecs.nfsclient.nfs.NfsSymlinkResponse;
import com.emc.ecs.nfsclient.nfs.NfsTime;
import com.emc.ecs.nfsclient.nfs.NfsType;
import com.emc.ecs.nfsclient.nfs.NfsWriteRequest;
import com.emc.ecs.nfsclient.nfs.NfsWriteResponse;

/**
 * Basic implementation of NfsFile. Subclasses need only implement constructors
 * and newChildFile().
 * 
 * @author seibed
 */
public abstract class NfsFileBase<N extends Nfs<F>, F extends NfsFile<N, F>> implements NfsFile<N, F> {

	/**
	 * The full network path to the file.
	 */
	private String _absolutePath;

	/**
	 * The real backing file, in case the file is a symbolic link.
	 */
	private F _backingFile;

	/**
	 * is it?
	 */
	private boolean _isRootFile = false;

	/**
	 * file handle for NFS calls
	 */
	private byte[] _fileHandle;

	/**
	 * The short name of the file, starting from the parent path.
	 */
	private String _name;

	/**
	 * The supporting NFS client.
	 */
	private final N _nfs;

	/**
	 * The full parent path of the file, starting with the mount point.
	 */
	private String _parent;

	/**
	 * The parent file, stored to reduce lookup overhead
	 */
	private F _parentFile;

	/**
	 * The full path of the file, starting with the mount point.
	 */
	private String _path;

	/**
	 * The basic constructor.
	 * 
	 * @param nfs         The supporting NFS client.
	 * @param path        The full path of the file, starting with the mount point.
	 * @param linkTracker The tracker to use. This must be passed so that monitoring
	 *                    is continued until the link resolves to a file that is not
	 *                    a symbolic link.
	 * @throws IOException
	 */
	public NfsFileBase(N nfs, String path, LinkTracker<N, F> linkTracker) throws IOException {
		if (nfs == null) {
			throw new IllegalArgumentException("Nfs instance can not be null");
		}
		_nfs = nfs;
		_parent = makeParentPath(path);
		F parent = isRootPath(path) ? null : newFile(_parent, linkTracker);
		setParentFileAndName(parent, makeName(path), linkTracker);
	}

	/**
	 * The most efficient constructor if the parent file already exists.
	 * 
	 * @param parentFile The parent file, stored to reduce lookup overhead
	 * @param childName  The short name of the file, starting from the parent path.
	 * @throws IOException
	 */
	public NfsFileBase(F parentFile, String childName) throws IOException {
		_nfs = parentFile.getNfs();
		setParentFileAndName(parentFile, childName, null);
	}

	/**
	 * @param path
	 * @return Everything up to and including the first separator character before
	 *         the final name.
	 */
	public static String makeParentPath(String path) {
		int firstParentSeparatorIndex = getParentSeparatorIndex(path);
		while ((firstParentSeparatorIndex > 0) && (separatorChar == path.charAt(firstParentSeparatorIndex))) {
			--firstParentSeparatorIndex;
		}
		int lengthOfParentName = (firstParentSeparatorIndex == 0) ? 1 : (firstParentSeparatorIndex + 2);
		return path.substring(0, lengthOfParentName);
	}

	/**
	 * @param path
	 * @return Everything after the last separator character.
	 */
	public static String makeName(String path) {
		int nameStartIndex = getParentSeparatorIndex(path) + 1;
		int endOfNameIndex = path.indexOf(separatorChar, nameStartIndex);
		if (endOfNameIndex < 0) {
			endOfNameIndex = path.length();
		}
		return path.substring(nameStartIndex, endOfNameIndex);
	}

	/**
	 * @param parent
	 * @param child
	 * @return the full child path
	 */
	public static String makeChildPath(String parent, String child) {
		StringBuilder stringBuilder = new StringBuilder(parent.replaceAll("/+", separator));
		if (!parent.endsWith(separator)) {
			stringBuilder.append(separatorChar);
		}
		return stringBuilder.append(child).toString();
	}

	/**
	 * @param path
	 * @return the separator index
	 */
	private static int getParentSeparatorIndex(String path) {
		int firstTerminalSeparatorIndex = path.length() - 1;
		while ((firstTerminalSeparatorIndex > 0) && (separatorChar == path.charAt(firstTerminalSeparatorIndex))) {
			--firstTerminalSeparatorIndex;
		}
		return path.lastIndexOf(separatorChar, firstTerminalSeparatorIndex);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#canDelete()
	 */
	@Override
	public boolean canDelete() throws IOException {
		return canAccess(Nfs.ACCESS3_DELETE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#canExecute()
	 */
	@Override
	public boolean canExecute() throws IOException {
		return canAccess(Nfs.ACCESS3_EXECUTE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#canExtend()
	 */
	@Override
	public boolean canExtend() throws IOException {
		return canAccess(Nfs.ACCESS3_EXTEND);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#canLookup()
	 */
	@Override
	public boolean canLookup() throws IOException {
		return canAccess(Nfs.ACCESS3_LOOKUP);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#canModify()
	 */
	@Override
	public boolean canModify() throws IOException {
		return canAccess(Nfs.ACCESS3_MODIFY);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#canRead()
	 */
	@Override
	public boolean canRead() throws IOException {
		return canAccess(Nfs.ACCESS3_READ);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public final int compareTo(F o) {
		if (o == null) {
			return 1;
		}

		return getAbsolutePath().compareTo(o.getAbsolutePath());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#createNewFile()
	 */
	@Override
	public boolean createNewFile() throws IOException {
		try {
			create(NfsCreateMode.GUARDED, new NfsSetAttributes(), null);
		} catch (NfsException e) {
			if (e.getStatus() == NfsStatus.NFS3ERR_EXIST) {
				return false;
			}
			throw e;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#delete()
	 */
	@Override
	public void delete() throws IOException {
		if (isDirectory()) {
			rmdir();
		} else {
			remove();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public final boolean equals(Object obj) {
		if (!(obj instanceof NfsFile)) {
			return false;
		}
		return getAbsolutePath().equals(((NfsFile<?, ?>) obj).getAbsolutePath());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#exists()
	 */
	@Override
	public boolean exists() throws IOException {
		boolean exists = false;
		try {
			// force lookup again
			setFileHandle(null);
			exists = (getFileHandle() != null);
		} catch (FileNotFoundException e) {
			// do nothing
		}
		return exists;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.io.NfsFile#followLinks()
	 */
	@Override
	public F followLinks() throws IOException {
		return followLinks(null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.io.NfsFile#followLinks(com.emc.ecs.nfsclient.
	 * nfs.io.LinkTracker)
	 */
	@Override
	public F followLinks(LinkTracker<N, F> linkTracker) throws IOException {
		if (_backingFile == null) {
			@SuppressWarnings("unchecked")
			F backingFile = (F) this;
			NfsGetAttributes attributes = safeGetAttributes(backingFile);
			while (NfsType.NFS_LNK == attributes.getType()) {
				if (linkTracker == null) {
					linkTracker = new LinkTracker<N, F>();
				}
				F newBackingFile = linkTracker.addLink(backingFile.getPath());
				if (newBackingFile != null) {
					backingFile = newBackingFile;
				} else {
					backingFile = followLink(backingFile.readlink().getData(), linkTracker);
				}
				attributes = safeGetAttributes(backingFile);
			}
			_backingFile = backingFile;
			if (linkTracker != null) {
				linkTracker.addResolvedPath(getPath(), _backingFile);
			}
		}

		return _backingFile;
	}

	/**
	 * @param file The file for which the attributes are wanted.
	 * @return The attributes, or empty attributes if they cannot be read.
	 */
	private NfsGetAttributes safeGetAttributes(F file) {
		if (file != null) {
			try {
				return file.getAttributes();
			} catch (Exception e) {
				// do nothing, this is expected
			}
		}
		return new NfsGetAttributes();
	}

	/**
	 * @param target      The symbolic link data
	 * @param linkTracker The tracker to use. This must be passed so that monitoring
	 *                    is continued until the link resolves to a file that is not
	 *                    a symbolic link.
	 * @return The file (which may itself be a link) corresponding to that path.
	 * @throws IOException if the target is blank or not in the mount point.
	 */
	private F followLink(String target, LinkTracker<N, F> linkTracker) throws IOException {
		if (StringUtils.isBlank(target)) {
			throw new IOException("blank link target");
		}

		if (!target.contains(separator)) {
			return getParentFile().newChildFile(target);
		} else {
			String path;
			if (target.startsWith(NfsFile.separator)) {
				if (!target.startsWith(getNfs().getExportedPath())) {
					throw new IOException("unreachable link target: " + target);
				} else {
					path = target.substring(getNfs().getExportedPath().length());
					if (!path.startsWith(separator)) {
						path = separator + path;
					}
				}
			} else {
				String parentPath = getParentFile().getPath();
				if (parentPath.endsWith(separator)) {
					path = parentPath + target;
				} else {
					path = parentPath + separator + target;
				}
			}
			return newFile(path, linkTracker);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getAbsolutePath()
	 */
	@Override
	public final String getAbsolutePath() {
		return _absolutePath;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#hasAccess(long)
	 */
	@Override
	public long getAccess(long accessToCheck) throws IOException {
		return access(accessToCheck).getAccess();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getAttributes()
	 */
	@Override
	public NfsGetAttributes getAttributes() throws IOException {
		return getNfs().wrapped_getAttr(getNfs().makeGetAttrRequest(getFileHandle())).getAttributes();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getChildFile(java.lang.String)
	 */
	@Override
	public F getChildFile(String childName) throws IOException {
		return newChildFile(childName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getFileHandle()
	 */
	@Override
	public byte[] getFileHandle() throws IOException {
		if (_isRootFile) {
			return getNfs().getRootFileHandle();
		}
		if (_fileHandle == null) {
			setFileHandle();
		}
		return (_fileHandle == null) ? null : _fileHandle.clone();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#getFreeSpace()
	 */
	@Override
	public long getFreeSpace() throws IOException {
		return fsstat().getFsStat().fbytes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#getMode()
	 */
	@Override
	public long getMode() throws IOException {
		return getAttributes().getMode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getName()
	 */
	@Override
	public final String getName() {
		return _name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getNfs()
	 */
	@Override
	public final N getNfs() {
		return _nfs;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getParent()
	 */
	@Override
	public final String getParent() {
		return _parent;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getParentFile()
	 */
	@Override
	public F getParentFile() {
		return _parentFile;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#getPath()
	 */
	@Override
	public final String getPath() {
		return _path;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#getTotalSpace()
	 */
	@Override
	public long getTotalSpace() throws IOException {
		return fsstat().getFsStat().tbytes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#getUsableSpace()
	 */
	@Override
	public long getUsableSpace() throws IOException {
		return fsstat().getFsStat().bytes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public final int hashCode() {
		return getAbsolutePath().hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#isDirectory()
	 */
	@Override
	public boolean isDirectory() throws IOException {
		return getAttributes().getType() == NfsType.NFS_DIR;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#isFile()
	 */
	@Override
	public boolean isFile() throws IOException {
		return getAttributes().getType() == NfsType.NFS_REG;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.io.NfsFile#isRootFile()
	 */
	@Override
	public boolean isRootFile() throws IOException {
		return _isRootFile;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#lastModified()
	 */
	@Override
	public long lastModified() throws IOException {
		return getAttributes().getMtime().getTimeInMillis();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#length()
	 */
	@Override
	public long length() {
		try {
			return lengthEx();
		} catch (IOException e) {
			return 0;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#lengthEx()
	 */
	@Override
	public long lengthEx() throws IOException {
		return getAttributes().getSize();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#list()
	 */
	@Override
	public List<String> list() throws IOException {
		boolean eof = false;
		long cookie = 0;
		long cookieverf = 0;
		final int count = 8 * 1024;
		List<NfsDirectoryEntry> entries = new ArrayList<NfsDirectoryEntry>(32);
		do {
			NfsReaddirResponse response = readdir(cookie, cookieverf, count, entries);
			eof = response.isEof();
			cookie = response.getCookie();
			cookieverf = response.getCookieverf();
		} while (!eof);
		List<String> children = new ArrayList<String>();
		for (NfsDirectoryEntry entry : entries) {
			if (!(".".equals(entry.getFileName()) || "..".equals(entry.getFileName()))) {
				children.add(entry.getFileName());
			}
		}
		return children;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#list(com.emc.ecs.nfsclient.util.
	 * NfsFilenameFilter)
	 */
	@Override
	public List<String> list(NfsFilenameFilter filter) throws IOException {
		List<String> children = list();
		if ((children == null) || (children.size() == 0) || (filter == null)) {
			return children;
		}
		List<String> filteredChildren = new ArrayList<String>();
		for (String child : children) {
			if (filter.accept(this, child)) {
				filteredChildren.add(child);
			}
		}
		return filteredChildren;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#listFiles()
	 */
	@Override
	public List<F> listFiles() throws IOException {
		return getChildFiles(list());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#listFiles(com.emc.ecs.nfsclient.util.
	 * NfsFilenameFilter)
	 */
	@Override
	public List<F> listFiles(NfsFilenameFilter filter) throws IOException {
		return getChildFiles(list(filter));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#listFiles(com.emc.ecs.nfsclient.util.
	 * NfsFileFilter)
	 */
	@Override
	public List<F> listFiles(NfsFileFilter filter) throws IOException {
		List<F> childFiles = listFiles();
		if ((childFiles == null) || (childFiles.size() == 0) || (filter == null)) {
			return childFiles;
		}
		List<F> filteredFiles = new ArrayList<F>();
		for (F childFile : childFiles) {
			if (filter.accept(childFile)) {
				filteredFiles.add(childFile);
			}
		}
		return filteredFiles;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#mkdir()
	 */
	@Override
	public void mkdir() throws IOException {
		mkdir(new NfsSetAttributes());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#mkdirs()
	 */
	@Override
	public void mkdirs() throws IOException {
		if ((!getParent().equals(separator)) && (!getParentFile().exists())) {
			getParentFile().mkdirs();
		}
		mkdir();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#renameTo(com.emc.ecs.nfsclient.nfs.
	 * NfsFile)
	 */
	@Override
	public boolean renameTo(F destination) throws IOException {
		return rename(destination).stateIsOk();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#setLastModified(long)
	 */
	@Override
	public void setLastModified(long millis) throws IOException {
		setAttributes(new NfsSetAttributes(null, null, null, NfsTime.DO_NOT_CHANGE, new NfsTime(millis)));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public final String toString() {
		return getAbsolutePath();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.emc.ecs.nfsclient.nfs.io.NfsFile#setAttributes(com.emc.ecs.nfsclient.
	 * nfs.NfsSetAttributes)
	 */
	@Override
	public void setAttributes(NfsSetAttributes nfsSetAttr) throws IOException {
		setattr(nfsSetAttr, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.util.NfsFile#changeMode(long)
	 */
	@Override
	public void setMode(long mode) throws IOException {
		setAttributes(
				new NfsSetAttributes(Long.valueOf(mode), null, null, NfsTime.DO_NOT_CHANGE, NfsTime.DO_NOT_CHANGE));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#readlink()
	 */
	@Override
	public NfsGetAttrResponse getattr() throws IOException {
		return getNfs().wrapped_getAttr(makeGetAttrRequest());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeGetAttrRequest()
	 */
	@Override
	public NfsGetAttrRequest makeGetAttrRequest() throws IOException {
		return getNfs().makeGetAttrRequest(getFileHandle());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#setattr(com.emc.ecs.nfsclient.nfs.
	 * NfsSetAttributes, com.emc.ecs.nfsclient.nfs.NfsTime)
	 */
	@Override
	public NfsSetAttrResponse setattr(NfsSetAttributes attributes, NfsTime guardTime) throws IOException {
		return getNfs().wrapped_setAttr(makeSetAttrRequest(attributes, guardTime));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeSetAttrRequest(com.emc.ecs.
	 * nfsclient.nfs.NfsSetAttributes, com.emc.ecs.nfsclient.nfs.NfsTime)
	 */
	@Override
	public NfsSetAttrRequest makeSetAttrRequest(NfsSetAttributes attributes, NfsTime guardTime) throws IOException {
		return getNfs().makeSetAttrRequest(getFileHandle(), attributes, guardTime);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#lookup()
	 */
	@Override
	public NfsLookupResponse lookup() throws IOException {
		return getNfs().wrapped_getLookup(makeLookupRequest());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeLookupRequest()
	 */
	@Override
	public NfsLookupRequest makeLookupRequest() throws IOException {
		return getNfs().makeLookupRequest(getParentFile().getFileHandle(), getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#access(long)
	 */
	@Override
	public NfsAccessResponse access(long accessToCheck) throws IOException {
		return getNfs().wrapped_getAccess(makeAccessRequest(accessToCheck));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeAccessRequest(long)
	 */
	@Override
	public NfsAccessRequest makeAccessRequest(long accessToCheck) throws IOException {
		return getNfs().makeAccessRequest(getFileHandle(), accessToCheck);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#readlink()
	 */
	@Override
	public NfsReadlinkResponse readlink() throws IOException {
		return getNfs().wrapped_getReadlink(makeReadlinkRequest());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeReadlinkRequest()
	 */
	@Override
	public NfsReadlinkRequest makeReadlinkRequest() throws IOException {
		return getNfs().makeReadlinkRequest(getFileHandle());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#read(long, int, byte[], int)
	 */
	@Override
	public NfsReadResponse read(long offset, int size, byte[] bytes, int position) throws IOException {
		return getNfs().wrapped_getRead(makeReadRequest(offset, size), bytes, position);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeReadRequest(long, int)
	 */
	@Override
	public NfsReadRequest makeReadRequest(long offset, int size) throws IOException {
		return getNfs().makeReadRequest(getFileHandle(), offset, size);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#write(long, java.util.List, int)
	 */
	@Override
	public NfsWriteResponse write(long offset, List<ByteBuffer> payload, int syncType) throws IOException {
		return getNfs().wrapped_sendWrite(makeWriteRequest(offset, payload, syncType));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#write(long, java.util.List, int,
	 * java.lang.Long)
	 */
	@Override
	public NfsWriteResponse write(long offset, List<ByteBuffer> payload, int syncType, Long verifier)
			throws IOException {
		return getNfs().wrapped_sendWrite(makeWriteRequest(offset, payload, syncType), verifier);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeWriteRequest(long, java.util.List,
	 * int)
	 */
	@Override
	public NfsWriteRequest makeWriteRequest(long offset, List<ByteBuffer> payload, int syncType) throws IOException {
		return getNfs().makeWriteRequest(getFileHandle(), offset, payload, syncType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#create(com.emc.ecs.nfsclient.nfs.
	 * NfsCreateMode, com.emc.ecs.nfsclient.nfs.NfsSetAttributes)
	 */
	@Override
	public NfsCreateResponse create(NfsCreateMode createMode, NfsSetAttributes attributes, byte[] verifier)
			throws IOException {
		NfsCreateResponse response = getNfs().wrapped_sendCreate(getNfs().makeCreateRequest(createMode,
				getParentFile().getFileHandle(), getName(), attributes, verifier));
		setFileHandle(response.getFileHandle());
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.emc.ecs.nfsclient.nfs.NfsFile#makeCreateRequest(com.emc.ecs.nfsclient
	 * .nfs.NfsCreateMode, com.emc.ecs.nfsclient.nfs.NfsSetAttributes)
	 */
	@Override
	public NfsCreateRequest makeCreateRequest(NfsCreateMode createMode, NfsSetAttributes attributes, byte[] verifier)
			throws IOException {
		return getNfs().makeCreateRequest(createMode, getParentFile().getFileHandle(), getName(), attributes, verifier);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#mkdir(com.emc.ecs.nfsclient.nfs.
	 * NfsSetAttributes)
	 */
	@Override
	public NfsMkdirResponse mkdir(NfsSetAttributes attributes) throws IOException {
		NfsMkdirResponse response = getNfs().wrapped_sendMkdir(makeMkdirRequest(attributes));
		setFileHandle(response.getFileHandle());
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.emc.ecs.nfsclient.nfs.NfsFile#makeMkdirRequest(com.emc.ecs.nfsclient.
	 * nfs.NfsSetAttributes)
	 */
	@Override
	public NfsMkdirRequest makeMkdirRequest(NfsSetAttributes attributes) throws IOException {
		return getNfs().makeMkdirRequest(getParentFile().getFileHandle(), getName(), attributes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#symlink(java.lang.String,
	 * com.emc.ecs.nfsclient.nfs.NfsSetAttributes)
	 */
	@Override
	public NfsSymlinkResponse symlink(String symbolicLinkData, NfsSetAttributes attributes) throws IOException {
		NfsSymlinkResponse response = getNfs().wrapped_sendSymlink(makeSymlinkRequest(symbolicLinkData, attributes));
		setFileHandle(response.getFileHandle());
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeSymlinkRequest(java.lang.String,
	 * com.emc.ecs.nfsclient.nfs.NfsSetAttributes)
	 */
	@Override
	public NfsSymlinkRequest makeSymlinkRequest(String symbolicLinkData, NfsSetAttributes attributes)
			throws IOException {
		return getNfs().makeSymlinkRequest(symbolicLinkData, getParentFile().getFileHandle(), getName(), attributes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#mknod(com.emc.ecs.nfsclient.nfs.
	 * NfsType, com.emc.ecs.nfsclient.nfs.NfsSetAttributes, long[])
	 */
	@Override
	public NfsMknodResponse mknod(NfsType type, NfsSetAttributes attributes, long[] rdev) throws IOException {
		NfsMknodResponse response = getNfs().wrapped_sendMknod(makeMknodRequest(type, attributes, rdev));
		setFileHandle(response.getFileHandle());
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.emc.ecs.nfsclient.nfs.NfsFile#makeMknodRequest(com.emc.ecs.nfsclient.
	 * nfs.NfsType, com.emc.ecs.nfsclient.nfs.NfsSetAttributes, long[])
	 */
	@Override
	public NfsMknodRequest makeMknodRequest(NfsType type, NfsSetAttributes attributes, long[] rdev) throws IOException {
		return getNfs().makeMknodRequest(getParentFile().getFileHandle(), getName(), type, attributes, rdev);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#remove()
	 */
	@Override
	public NfsRemoveResponse remove() throws IOException {
		NfsRemoveResponse response = getNfs().wrapped_sendRemove(makeRemoveRequest());
		setFileHandle(null);
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeRemoveRequest()
	 */
	@Override
	public NfsRemoveRequest makeRemoveRequest() throws IOException {
		return getNfs().makeRemoveRequest(getParentFile().getFileHandle(), getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#rmdir()
	 */
	@Override
	public NfsRmdirResponse rmdir() throws IOException {
		NfsRmdirResponse response = getNfs().wrapped_sendRmdir(makeRmdirRequest());
		setFileHandle(null);
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeRmdirRequest()
	 */
	@Override
	public NfsRmdirRequest makeRmdirRequest() throws IOException {
		return getNfs().makeRmdirRequest(getParentFile().getFileHandle(), getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#rename(com.emc.ecs.nfsclient.nfs.
	 * NfsFile)
	 */
	@Override
	public NfsRenameResponse rename(F toFile) throws IOException {
		NfsRenameResponse response = getNfs().wrapped_sendRename(makeRenameRequest(toFile));
		if (response.stateIsOk()) {
			setPathFields(toFile);
		}
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.emc.ecs.nfsclient.nfs.NfsFile#makeRenameRequest(com.emc.ecs.nfsclient
	 * .nfs.NfsFile)
	 */
	@Override
	public NfsRenameRequest makeRenameRequest(F toFile) throws IOException {
		return getNfs().makeRenameRequest(getParentFile().getFileHandle(), getName(),
				toFile.getParentFile().getFileHandle(), toFile.getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.emc.ecs.nfsclient.nfs.NfsFile#link(com.emc.ecs.nfsclient.nfs.NfsFile)
	 */
	@Override
	public NfsLinkResponse link(F source) throws IOException {
		NfsLinkResponse response = getNfs().wrapped_sendLink(makeLinkRequest(source));
		setFileHandle(source.getFileHandle());
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeLinkRequest(com.emc.ecs.nfsclient.
	 * nfs.NfsFile)
	 */
	@Override
	public NfsLinkRequest makeLinkRequest(F source) throws IOException {
		return getNfs().makeLinkRequest(source.getFileHandle(), getParentFile().getFileHandle(), getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#readdir(long, long, int)
	 */
	@Override
	public NfsReaddirResponse readdir(long cookie, long cookieverf, int count) throws IOException {
		return getNfs().wrapped_getReaddir(makeReaddirRequest(cookie, cookieverf, count));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#readdir(long, long, int,
	 * java.util.List)
	 */
	@Override
	public NfsReaddirResponse readdir(long cookie, long cookieverf, int count, List<NfsDirectoryEntry> entries)
			throws IOException {
		return getNfs().wrapped_getReaddir(makeReaddirRequest(cookie, cookieverf, count), entries);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeReaddirRequest(long, long, int)
	 */
	@Override
	public NfsReaddirRequest makeReaddirRequest(long cookie, long cookieverf, int count) throws IOException {
		return getNfs().makeReaddirRequest(followLinks().getFileHandle(), cookie, cookieverf, count);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#readdirplus(long, long, int, int)
	 */
	@Override
	public NfsReaddirplusResponse readdirplus(long cookie, long cookieverf, int dircount, int maxcount)
			throws IOException {
		return getNfs().wrapped_getReaddirplus(makeReaddirplusRequest(cookie, cookieverf, dircount, maxcount));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#readdirplus(long, long, int, int,
	 * java.util.List)
	 */
	@Override
	public NfsReaddirplusResponse readdirplus(long cookie, long cookieverf, int dircount, int maxcount,
			List<NfsDirectoryPlusEntry> entries) throws IOException {
		return getNfs().wrapped_getReaddirplus(makeReaddirplusRequest(cookie, cookieverf, dircount, maxcount), entries);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeReaddirplusRequest(long, long,
	 * int, int)
	 */
	@Override
	public NfsReaddirplusRequest makeReaddirplusRequest(long cookie, long cookieverf, int dircount, int maxcount)
			throws IOException {
		return getNfs().makeReaddirplusRequest(followLinks().getFileHandle(), cookie, cookieverf, dircount, maxcount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#fsstat()
	 */
	@Override
	public NfsFsStatResponse fsstat() throws IOException {
		return getNfs().wrapped_getFsStat(getNfs().makeFsStatRequest());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeFsStatRequest()
	 */
	@Override
	public NfsFsStatRequest makeFsStatRequest() throws IOException {
		return getNfs().makeFsStatRequest(getFileHandle());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#fsinfo()
	 */
	@Override
	public NfsFsInfoResponse fsinfo() throws IOException {
		return getNfs().wrapped_getFsInfo(getNfs().makeFsInfoRequest());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeFsInfoRequest()
	 */
	@Override
	public NfsFsInfoRequest makeFsInfoRequest() throws IOException {
		return getNfs().makeFsInfoRequest(getFileHandle());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#pathconf()
	 */
	@Override
	public NfsPathconfResponse pathconf() throws IOException {
		return getNfs().wrapped_getPathconf(makePathconfRequest());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makePathconfRequest()
	 */
	@Override
	public NfsPathconfRequest makePathconfRequest() throws IOException {
		return getNfs().makePathconfRequest(getFileHandle());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#commit(long, int)
	 */
	@Override
	public NfsCommitResponse commit(long offsetToCommit, int dataSizeToCommit) throws IOException {
		return getNfs().wrapped_sendCommit(makeCommitRequest(offsetToCommit, dataSizeToCommit));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.emc.ecs.nfsclient.nfs.NfsFile#makeCommitRequest(long, int)
	 */
	@Override
	public NfsCommitRequest makeCommitRequest(long offsetToCommit, int dataSizeToCommit) throws IOException {
		return getNfs().makeCommitRequest(getFileHandle(), offsetToCommit, dataSizeToCommit);
	}

	/**
	 * Conversion method.
	 * 
	 * @param childNames A list of child names.
	 * @return The list of child files, one for each name.
	 * @throws IOException
	 */
	protected List<F> getChildFiles(List<String> childNames) throws IOException {
		if (childNames == null) {
			return null;
		}
		List<F> childFiles = new ArrayList<F>(childNames.size());
		for (String childName : childNames) {
			childFiles.add(getChildFile(childName));
		}
		return childFiles;
	}

	/**
	 * @return true if it is, false if it is not
	 */
	private final boolean isRootPath(String path) {
		return (path == null) || "".equals(path) || separator.equals(path);
	}

	/**
	 * @param fileHandle
	 */
	protected final void setFileHandle(byte[] fileHandle) {
		_fileHandle = fileHandle;
	}

	/**
	 * This creates a new file with arbitrary path.
	 * 
	 * @param path the absolute path from the mount point.
	 * @return the new nfs file.
	 * @throws IOException
	 */
	protected final F newFile(String path) throws IOException {
		return newFile(path, null);
	}

	/**
	 * This creates a new file with arbitrary path, using the given LinkTracker.
	 * 
	 * @param path        the absolute path from the mount point.
	 * @param linkTracker The tracker to use. This must be passed so that monitoring
	 *                    is continued until the link resolves to a file that is not
	 *                    a symbolic link.
	 * @return A new file corresponding to the path, created using the linkTracker
	 */
	protected abstract F newFile(String path, LinkTracker<N, F> linkTracker) throws IOException;

	/**
	 * This method handles special cases, such as symbolic links in the parent
	 * directory, empty filenames, or the special names "." and "..". The algorithm
	 * required is simplified by the fact that special cases for the parent file are
	 * handled before this is called, as the path is always resolved from the bottom
	 * up. This means that the special cases have already been resolved for the
	 * parents and all supporting ancestors, so those possibilities need only be
	 * considered at the current level, eliminating any need for explicit recursive
	 * handling here.
	 * 
	 * @param parentFile  The original parent file. This may be changed for cases
	 *                    that require special handling, e.g., symbolic links, ".",
	 *                    "..", and empty names.
	 * @param name        The original name. This may also be changed for cases that
	 *                    require special handling.
	 * @param linkTracker The tracker to use. This must be passed so that monitoring
	 *                    is continued until the link resolves to a file that is not
	 *                    a symbolic link.
	 * @throws IOException if links cannot be followed.
	 */
	private void setParentFileAndName(F parentFile, String name, LinkTracker<N, F> linkTracker) throws IOException {
		if (parentFile != null) {
			parentFile = parentFile.followLinks(linkTracker);
			if (StringUtils.isBlank(name) || ".".equals(name)) {
				name = parentFile.getName();
				parentFile = parentFile.getParentFile();
			} else if ("..".equals(name)) {
				parentFile = parentFile.getParentFile();
				if (parentFile == null) {
					name = "";
				} else {
					name = parentFile.getName();
					parentFile = parentFile.getParentFile();
				}
			}
		}

		_parentFile = parentFile;
		_name = name;
		setPathFields();
	}

	/**
	 * 
	 */
	private void setPathFields() {
		if (_parentFile == null) {
			_parent = separator;
		} else {
			_parent = _parentFile.getPath();
			if (!_parent.endsWith(separator)) {
				_parent = _parent + separator;
			}
			if (StringUtils.isEmpty(_name)) {
				_path = _parent;
				_absolutePath = _parentFile.getAbsolutePath();
			} else {
				_path = _parent + separator + _name;
				_absolutePath = _parentFile.getAbsolutePath() + separator + _name;
			}
		}
		_isRootFile = (_parentFile == null);
		String absolutePathBase = (_parentFile != null) ? _parentFile.getAbsolutePath()
				: (getNfs().getServer() + ":" + getNfs().getExportedPath());
		if (!absolutePathBase.endsWith(separator)) {
			absolutePathBase = absolutePathBase + separator;
		}
		if (StringUtils.isEmpty(_name)) {
			_path = _parent;
			_absolutePath = absolutePathBase;
		} else {
			_path = _parent + _name;
			_absolutePath = absolutePathBase + _name;
		}
	}

	/**
	 * @param toFile
	 */
	protected final void setPathFields(F toFile) {
		_parentFile = toFile.getParentFile();
		_parent = toFile.getParent();
		_path = toFile.getPath();
		_absolutePath = toFile.getAbsolutePath();
		_name = toFile.getName();
	}

	/**
	 * @param accessToCheck
	 * @return <code>true</code> if the access is allowed, <code>false</code> if it
	 *         is not
	 * @throws IOException
	 */
	private boolean canAccess(long accessToCheck) throws IOException {
		return (accessToCheck & getAccess(accessToCheck)) != 0;
	}

	/**
	 * Set the file handle from the _path value
	 */
	private void setFileHandle() {
		byte[] fileHandle = null;
		if (_isRootFile) {
			fileHandle = getNfs().getRootFileHandle();
		} else {
			try {
				if (getParentFile().getFileHandle() != null) {
					fileHandle = getNfs().wrapped_getLookup(makeLookupRequest()).getFileHandle();
				}
			} catch (IOException e) {
				// do nothing, this will be a common exception
			}
		}
		setFileHandle(fileHandle);
	}

}
