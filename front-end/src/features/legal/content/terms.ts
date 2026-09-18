import type { Locale } from "@/i18n/routing";
import type { LegalContent } from "../components/LegalDocument";
import { CONTACT_EMAIL } from "./privacy";

const UPDATED = "2026-09-18";

/** Terms of Service — shown on /terms and linked from the Google OAuth consent screen. */
export const terms: Record<Locale, LegalContent> = {
  en: {
    title: "Terms of Service",
    updated: `Last updated: ${UPDATED}`,
    intro: [
      "These terms govern your use of Aura, a music-social streaming platform operated by an individual developer based in Bulgaria (“we”). By creating an account or using Aura you agree to them. If you do not agree, please do not use the service.",
      "Aura is currently in development and offered to a limited group of test users. Features may change, break or disappear without notice.",
    ],
    sections: [
      {
        heading: "Your account",
        body: [
          "You must be at least 16 years old. Keep your password confidential and tell us if you suspect someone else is using your account. You are responsible for what happens under your account. One person, one account.",
        ],
      },
      {
        heading: "Music and content",
        body: [
          "Aura does not host or license music. Catalog information comes from third-party providers (TIDAL, Last.fm, Discogs, LRCLIB) and playback happens through the embedded YouTube player, subject to YouTube's Terms of Service (https://www.youtube.com/t/terms). Availability of any track depends on those providers and may change at any time.",
          "Artwork, lyrics, biographies and other material remain the property of their respective rights holders. Aura displays them for the purpose of identifying and discovering music.",
        ],
      },
      {
        heading: "Your content",
        body: [
          "Playlists, likes, profile details and activity you create are yours. You grant us a licence to store and display them so the service works — including to the friends you have connected with, as the feature describes. You can delete them, or your whole account, at any time.",
        ],
      },
      {
        heading: "Acceptable use",
        body: [
          [
            "Do not use Aura to circumvent the rights of music providers or rights holders, or to download, record or redistribute audio.",
            "Do not scrape, overload, probe or reverse-engineer the service or its APIs.",
            "Do not impersonate others, harass people, or post unlawful content in usernames, playlist names or anywhere else.",
            "Do not share your account or use automated accounts.",
          ],
          "We may suspend or delete accounts that break these rules.",
        ],
      },
      {
        heading: "Privacy",
        body: ["How we handle personal data is described in the Privacy Policy, which is part of these terms."],
      },
      {
        heading: "No warranty",
        body: [
          "Aura is provided “as is”, without warranties of any kind. We do not promise that the service will be available, error-free, or that any particular music will be playable. To the extent permitted by law, we are not liable for indirect or consequential damages arising from your use of Aura. Nothing in these terms limits rights you have as a consumer under Bulgarian or EU law.",
        ],
      },
      {
        heading: "Ending things",
        body: [
          "You can delete your account whenever you like. We may suspend or terminate access if you break these terms or if we shut the service down; where practical we will give notice.",
        ],
      },
      {
        heading: "Changes and law",
        body: [
          "We may update these terms; the date at the top tells you when. Continued use after a change means you accept it. These terms are governed by the laws of Bulgaria, and disputes are subject to the courts of Bulgaria, without prejudice to mandatory consumer protections in your country of residence.",
          `Questions: ${CONTACT_EMAIL}.`,
        ],
      },
    ],
    footer: { privacy: "Privacy Policy", terms: "Terms of Service", signIn: "Sign in" },
  },

  bg: {
    title: "Условия за ползване",
    updated: `Последна промяна: ${UPDATED}`,
    intro: [
      "Тези условия уреждат ползването на Aura — музикално-социална стрийминг платформа, поддържана от физическо лице в България („ние“). Като създаваш профил или ползваш Aura, ти ги приемаш. Ако не си съгласен, моля, не ползвай услугата.",
      "Aura е в разработка и се предлага на ограничен кръг тестови потребители. Функциите могат да се променят, да спрат да работят или да изчезнат без предизвестие.",
    ],
    sections: [
      {
        heading: "Твоят профил",
        body: [
          "Трябва да си навършил 16 години. Пази паролата си и ни уведоми, ако подозираш, че някой друг ползва профила ти. Ти отговаряш за случващото се в профила ти. Един човек — един профил.",
        ],
      },
      {
        heading: "Музика и съдържание",
        body: [
          "Aura не хоства и не лицензира музика. Каталожната информация идва от външни доставчици (TIDAL, Last.fm, Discogs, LRCLIB), а възпроизвеждането става през вградения YouTube плейър съгласно Условията за ползване на YouTube (https://www.youtube.com/t/terms). Наличността на всяка песен зависи от тези доставчици и може да се промени по всяко време.",
          "Обложки, текстове, биографии и други материали остават собственост на съответните правоносители. Aura ги показва с цел идентифициране и откриване на музика.",
        ],
      },
      {
        heading: "Твоето съдържание",
        body: [
          "Плейлистите, харесванията, данните в профила и активността, които създаваш, са твои. Даваш ни право да ги съхраняваме и показваме, за да работи услугата — включително на приятелите, с които си се свързал, както описва съответната функция. Можеш да ги изтриеш, както и целия си профил, по всяко време.",
        ],
      },
      {
        heading: "Допустимо ползване",
        body: [
          [
            "Не ползвай Aura, за да заобикаляш правата на музикалните доставчици или правоносители, нито за да сваляш, записваш или разпространяваш аудио.",
            "Не скрейпвай, не претоварвай, не сондирай и не прави обратно инженерство на услугата или API-тата ѝ.",
            "Не се представяй за друг, не тормози хора и не публикувай незаконно съдържание — в потребителски имена, имена на плейлисти или където и да е.",
            "Не споделяй профила си и не ползвай автоматизирани профили.",
          ],
          "Можем да спрем или изтрием профили, които нарушават тези правила.",
        ],
      },
      {
        heading: "Поверителност",
        body: ["Как обработваме личните данни е описано в Политиката за поверителност, която е част от тези условия."],
      },
      {
        heading: "Без гаранция",
        body: [
          "Aura се предоставя „както е“, без каквито и да е гаранции. Не обещаваме, че услугата ще бъде достъпна, без грешки, или че определена музика ще може да се пусне. Доколкото законът позволява, не отговаряме за косвени или последващи вреди от ползването на Aura. Нищо в тези условия не ограничава правата ти като потребител по българското или европейското право.",
        ],
      },
      {
        heading: "Прекратяване",
        body: [
          "Можеш да изтриеш профила си когато пожелаеш. Ние можем да спрем или прекратим достъпа при нарушение на тези условия или ако затворим услугата; когато е възможно, ще уведомим предварително.",
        ],
      },
      {
        heading: "Промени и приложимо право",
        body: [
          "Можем да обновяваме тези условия; датата горе показва кога. Продължаването на ползването след промяна означава, че я приемаш. Условията се уреждат от законите на Република България, а споровете — от българските съдилища, без да се засягат императивните потребителски защити в държавата ти на пребиваване.",
          `Въпроси: ${CONTACT_EMAIL}.`,
        ],
      },
    ],
    footer: { privacy: "Политика за поверителност", terms: "Условия за ползване", signIn: "Вход" },
  },
};
