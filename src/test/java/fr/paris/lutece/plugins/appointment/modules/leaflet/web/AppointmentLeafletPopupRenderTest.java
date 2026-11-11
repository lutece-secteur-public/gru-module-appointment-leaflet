/*
 * Copyright (c) 2002-2026, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.appointment.modules.leaflet.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

/**
 * Renders the map popup of an appointment form with HTML auto-escaping (LUT-33153): the popup is set as HTML in the
 * Leaflet map, so the title and the address of the form are escaped, and the booking link holds each URL separator
 * once. The core macros the template calls are replaced by stand-ins that write their arguments (escaped) and their
 * nested content: only the FreeMarker engine is needed.
 */
public class AppointmentLeafletPopupRenderTest
{
    private static final String TEMPLATES_ROOT = "webapp/WEB-INF/templates";
    private static final String TEMPLATE_POPUP = "skin/plugins/appointment/modules/leaflet/popup_appointment.html";

    /** Stand-ins of the core macros the template calls. */
    private static final String MACROS = "macros.ftl";
    private static final String MACROS_SOURCE = "<#macro cBlock id='' rest...><div id=\"${id}\"><#nested></div></#macro>"
            + "<#macro cTitle level=2 rest...><h${level}><#nested></h${level}></#macro><#macro cText rest...><p><#nested></p></#macro>"
            + "<#macro cLink href='' label='' rest...><a href=\"${href}\">${label}<#nested></a></#macro>";

    private static final String HOSTILE = "Mairie <b>x</b> \"q\" <script>alert(1)</script>";
    private static final String HOSTILE_ESCAPED = "Mairie &lt;b&gt;x&lt;/b&gt; &quot;q&quot; &lt;script&gt;alert(1)&lt;/script&gt;";

    /**
     * Renders the popup template with the core configuration of the output format, in French: a number written without
     * ?c would get a grouping separator.
     *
     * @param model
     *            the model
     * @return the HTML
     * @throws IOException
     *             if the template cannot be read
     * @throws TemplateException
     *             if the rendering fails
     */
    private static String render( Map<String, Object> model ) throws IOException, TemplateException
    {
        StringTemplateLoader macros = new StringTemplateLoader( );
        macros.putTemplate( MACROS, MACROS_SOURCE );
        Configuration cfg = new Configuration( Configuration.VERSION_2_3_0 );
        cfg.setOutputFormat( HTMLOutputFormat.INSTANCE );
        cfg.setAutoEscapingPolicy( Configuration.ENABLE_IF_DEFAULT_AUTO_ESCAPING_POLICY );
        cfg.setLocale( Locale.FRANCE );
        cfg.setTemplateLoader( new MultiTemplateLoader( new TemplateLoader [ ] {
                macros, new FileTemplateLoader( new File( TEMPLATES_ROOT ) )
        } ) );
        cfg.addAutoInclude( MACROS );
        StringWriter writer = new StringWriter( );
        cfg.getTemplate( TEMPLATE_POPUP ).process( model, writer );

        return writer.toString( );
    }

    /**
     * An appointment form whose title and address hold markup.
     *
     * @return the form
     */
    private static AppointmentFormDTO form( )
    {
        AppointmentFormDTO form = new AppointmentFormDTO( );
        form.setIdForm( 1234 );
        form.setTitle( HOSTILE );
        form.setAddress( "1 rue <i>a</i> & b" );

        return form;
    }

    /**
     * The popup escapes the title and the address of the form and writes the booking link with plain separators.
     *
     * @throws Exception
     *             if the rendering fails
     */
    @Test
    public void testPopupEscapesTheFormAndLinksToTheCalendar( ) throws Exception
    {
        Map<String, Object> model = new HashMap<>( );
        model.put( "appointment_form", form( ) );
        model.put( "first_slot_date", "12/11/2026" );
        model.put( "first_slot_time", "09:30" );
        String strHtml = render( model );

        assertTrue( strHtml.contains( "<h2>" + HOSTILE_ESCAPED + "</h2>" ), strHtml );
        assertTrue( strHtml.contains( "<p>1 rue &lt;i&gt;a&lt;/i&gt; &amp; b</p>" ), strHtml );
        assertFalse( strHtml.contains( "<script>" ), strHtml );
        assertTrue( strHtml.contains( "href=\"jsp/site/Portal.jsp?page=appointment&amp;view=getViewAppointmentCalendar&amp;id_form=1234\"" ), strHtml );
        assertFalse( strHtml.contains( "&amp;amp;" ), strHtml );
    }

    /**
     * Without a free slot, the popup shows the message and no booking link.
     *
     * @throws Exception
     *             if the rendering fails
     */
    @Test
    public void testPopupWithoutSlotHasNoLink( ) throws Exception
    {
        Map<String, Object> model = new HashMap<>( );
        model.put( "appointment_form", form( ) );
        String strHtml = render( model );

        assertTrue( strHtml.contains( "#i18n{module.appointment.leaflet.noSlotAvailable}" ), strHtml );
        assertFalse( strHtml.contains( "<a " ), strHtml );
    }
}
