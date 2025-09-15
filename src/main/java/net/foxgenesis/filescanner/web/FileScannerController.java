package net.foxgenesis.filescanner.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.dv8tion.jda.api.entities.Guild;
import net.foxgenesis.filescanner.database.FileScannerConfiguration;
import net.foxgenesis.filescanner.database.FileScannerConfigurationService;
import net.foxgenesis.watame.web.WebPanelUtil;
import net.foxgenesis.watame.web.annotation.PluginMapping;
import net.foxgenesis.watame.web.security.DiscordOAuth2User;

@PluginMapping(plugin = "filescanner")
public class FileScannerController {

	@Autowired
	private FileScannerConfigurationService database;

	@Autowired
	private WebPanelUtil util;

	@GetMapping
	public String getView(Model model, @RequestAttribute Guild guild) {
		model.addAttribute("fileScannerConfiguration",
				database.getFresh(guild).orElseGet(() -> new FileScannerConfiguration(guild)));
		return "filescanner";
	}

	@PostMapping
	public String update(Model model, @AuthenticationPrincipal DiscordOAuth2User oauth2User,
			@RequestAttribute Guild guild, @Valid FileScannerConfiguration fileScannerConfiguration,
			BindingResult bindingResult, final HttpServletResponse res) {
		if (bindingResult.hasErrors()) {
			res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return "filescanner";
		}

		System.out.println(fileScannerConfiguration);
		fileScannerConfiguration.setGuild(guild.getIdLong());
		model.addAttribute("fileScannerConfiguration", database.save(fileScannerConfiguration));

		util.logConfigurationChange(oauth2User, guild, "filescanner");
		return "filescanner";
	}
}
