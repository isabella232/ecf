/****************************************************************************
 * Copyright (c) 2004 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/

package org.eclipse.ecf.datashare;

import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.datashare.events.IChannelEvent;
import org.eclipse.ecf.datashare.events.IChannelMessageEvent;

/**
 * Abstract class for sharing data using {@link IChannel}.  Subclasses should
 * be created as desired to share objects of different types.  Note that
 * to send data, subclasses can use {@link #sendMessage(ID, byte[])}, and
 * to receive data subclasses must implement {@link #handleMessage(byte[])}
 * or {@link #handleChannelEvent(IChannelEvent)}.
 */
public abstract class AbstractShare {

	protected IChannel channel = null;

	private IChannelListener listener = new IChannelListener() {
		public void handleChannelEvent(IChannelEvent event) {
			AbstractShare.this.handleChannelEvent(event);
		}
	};

	/**
	 * Handle reception of an IChannelEvent.  
	 * @param event the IChannelEvent received.  This implementation 
	 * detects instances of {@link IChannelMessageEvent} and
	 * calls {@link #handleMessage(byte[])} if found.  All other
	 * channel events are ignored.  Subclasses may override to detect
	 * and respond to other channel events as desired.
	 */
	protected void handleChannelEvent(IChannelEvent event) {
		if (event instanceof IChannelMessageEvent) {
			IChannelMessageEvent cme = (IChannelMessageEvent) event;
			handleMessage(cme.getFromContainerID(),cme.getData());
		}
	}
	
	public AbstractShare(IChannelContainerAdapter adapter) throws ECFException {
		Assert.isNotNull(adapter);
		channel = adapter.createChannel(IDFactory.getDefault().createStringID(
				this.getClass().getName()), listener, null);
	}

	public AbstractShare(IChannelContainerAdapter adapter, ID channelID)
			throws ECFException {
		this(adapter, channelID, null);
	}

	public AbstractShare(IChannelContainerAdapter adapter, ID channelID,
			Map options) throws ECFException {
		Assert.isNotNull(adapter);
		Assert.isNotNull(channelID);
		channel = adapter.createChannel(channelID, listener, options);
	}

	/**
	 * Receive message for this channel.  This method will be called asynchronously
	 * by an arbitrary thread when data to the associated channel is received.
	 * 
	 * @param fromContainerID the ID of the sender container.  Will not be <code>null</code>.
	 * @param data the data received on the channel.  Will not be <code>null</code>.
	 */
	protected abstract void handleMessage(ID fromContainerID, byte[] data);

	protected synchronized void sendMessage(ID toID, byte[] data)
			throws ECFException {
		if (channel != null)
			channel.sendMessage(toID, data);
	}

	public IChannel getChannel() {
		return channel;
	}

	public boolean isDisposed() {
		return (getChannel() == null);
	}
	
	public synchronized void dispose() {
		if (channel != null) {
			channel.dispose();
			channel = null;
		}
	}

}
