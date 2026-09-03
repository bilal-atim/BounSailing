---
title: Yelken Basarken Güvenlik Önlemleri
category: temel
order: 4
keywords: güvenlik, vinç, vinç dolama yönü, saat yönü, spinlock, stoper, yük, mandar, el sıkışması, kollu vinç, override, parmak
summary: Vinç dolama yönü, spinlocklar ve yük kontrolü — yelken basarken en sık yaralanma yaratan noktalar.
sources: seyirler-ve-manevralar, gezi-egitimleri-el-kitabi, 2-yildiz-teorik-kitabi
---

Teknede en sık yaralanma yelken basarken ve manevra sırasında, **yük altındaki halat ve vinç** yüzünden olur. Aşağıdakiler ezberlenmesi gereken kurallardır.

## 1. Vinç dolama yönü

Vinç, yelken halatlarını (ıskotaları) güçlü ve kontrollü şekilde çekmek için kullanılan mekanik yardımcıdır.

> **Vinç daima SAAT YÖNÜNDE sarılır.**

Ters yönde sarılan bir halat vinci kilitlemez, yük altında kayar ve elinizden fırlar.

- Halat vince **alttan üste doğru** ve sarım halkaları **üst üste binmeyecek** şekilde sarılır.
- Üst üste binen sarımlar **override** (kilitlenme) yaratır; yük altında bunu açmak çok zordur ve tehlikelidir.
- Sarım sayısı yüke göre ayarlanır: hafif havada 2, yük arttıkça 3-4 tur.
- Kolu takmadan önce halatın **kurt ağzına** (self-tailer) düzgün oturduğunu kontrol edin.

## 2. Eller ve parmaklar

- **Parmaklarınızı asla vinç ile halat arasına sokmayın.** Yük geldiğinde parmak kopar.
- Halatı vince sararken **avuç açık**, parmaklar halattan uzak tutulur.
- Vinç kolunu çevirirken bir başkası halatı besliyorsa göz teması kurun.
- Boşlanan bir ıskotanın önünde durmayın; yük altındaki halat kırbaç gibi hareket eder.

## 3. Spinlocklar (halat stoperleri)

Spinlock, halatı tek yönde kilitleyen mandaldır. Piyanoda yan yana dizilirler.

- **Hangi spinlockun hangi halatı tuttuğunu bilin.** Yanlış spinlock açmak, basılı bir yelkenin aniden inmesi demektir.
- Bir spinlocku **yük altındayken açmayın.** Önce halatı vince alıp yükü vince aktarın, sonra spinlocku açın.
- Yük vinçteyken spinlocku açtıktan sonra halatı **kontrollü** boşlayın.
- Kapatırken halatın stoper dişlerine tam oturduğundan emin olun.

## 4. Yük var mı kontrolü

Herhangi bir halata dokunmadan önce sorulacak soru: **"Bu halatın üzerinde yük var mı?"**

| Durum | Ne yapılır |
|---|---|
| Yük var, boşlanacak | Önce vince al, sonra kontrollü boşla |
| Yük var, alınacak | Vinç ve kol kullan, elle çekme |
| Yük yok | Elle çalışılabilir |

Yük altındaki bir halatı elle tutmaya çalışmak el yanığı ve düşme sebebidir. **Eldiven kullanın** — yelken eldiveni unutulmamalı.

## 5. Bumba ve baş üstü

- Ana yelken basılırken ve indirilirken **bumbanın altında durulmaz.**
- Direk dibinde çalışan kişi mandarın yönünü ve kendi durduğu yeri kontrol etmelidir.
- Baş tarafta çalışırken **en az 3 noktadan temas** kuralı geçerlidir.

## 6. İletişim

Her yelken basma bir ekip işidir. Komut verilmeden vinç çevrilmez, spinlock açılmaz. Kimin ne yapacağı [[yelken-basmadan-once|basmadan önce]] konuşulmuş olmalıdır.

İlgili konular: [[trimci]], [[basustu]], [[seyir-esnasinda-dikkat]]
