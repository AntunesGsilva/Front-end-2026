
const pet = document.getElementById("b");
const btn = document.getElementById("btn");
const bubble = document.getElementById("bubble");

const barFome = document.getElementById("bar-fome");
const barHumor = document.getElementById("bar-humor");

const valFome = document.getElementById("val-fome");
const valHumor = document.getElementById("val-humor");

let fome = 100;
let humor = 100;
let vivo = true;

let ultimoAviso = 0;

function falar(texto){
  bubble.textContent = texto;
  bubble.classList.add("show");

  setTimeout(() => {
    bubble.classList.remove("show");
  }, 2000);
}

function atualizarExpressao(){
  if(!vivo){
    pet.src = "b_d.png";
    return;
  }

  if(fome <= 30){
    pet.src = "b_p.png";
  }  

  else if(humor >= 70){
    pet.src = "b_a.png";
  } 
  else{
    pet.src = "b_n.png";
  }
}

function atualizar(){
  barFome.style.width = fome + "%";
  barHumor.style.width = humor + "%";

  valFome.textContent = Math.round(fome);
  valHumor.textContent = Math.round(humor);

  atualizarExpressao(); 
}

function loop(){
  if(!vivo) return;

  fome -= 1;
  humor -= 0.5;

  if(fome < 0) fome = 0;
  if(humor < 0) humor = 0;
  
  if(fome <= 30 && Date.now() - ultimoAviso > 3000){
    falar("Tô com fome 😢");
    ultimoAviso = Date.now();
  }

  if(fome <= 0){
    falar("Morri... 💀");
    vivo = false;
  }

  atualizar();
}

setInterval(loop, 1000);

btn.addEventListener("click", () => {
  if(!vivo) return;

  fome += 20;
  humor += 10;

  if(fome > 100) fome = 100;
  if(humor > 100) humor = 100;

  falar("Nhac! 😋");
  atualizar();
});

pet.addEventListener("click", () => {
  if(!vivo) return;

  humor += 5;

  if(humor > 100) humor = 100;

  falar("Hehe 😄");
  atualizar();
});

atualizar();


